import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private static Set<ClientHandler> clients = new HashSet<>();
    private static Map<String, ChatRoom> chatRooms = new HashMap<>();
    private static final int PORT = 12345;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            String ipAddress = getNetworkIPAddress();
            System.out.println("Chat Server started on port " + PORT);
            System.out.println("Server IP: " + ipAddress);
            System.out.println("Waiting for clients to connect...");

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected: " + socket.getInetAddress().getHostAddress());
                ClientHandler client = new ClientHandler(socket);
                clients.add(client);
                new Thread(client).start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String getNetworkIPAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()) continue;
                    if (addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (SocketException | UnknownHostException e) {
            return "localhost";
        }
    }

    public static synchronized void broadcast(String message, ChatRoom room, ClientHandler sender) {
        if (room != null) {
            room.broadcastMessage(message, sender);
        } else {
            for (ClientHandler client : clients) {
                if (client != sender) {
                    client.sendMessage(message);
                }
            }
        }
    }

    public static synchronized void updateRoomLists() {
        StringBuilder roomList = new StringBuilder("ROOMLIST|");
        chatRooms.forEach((name, room) -> {
            roomList.append(name).append("|");
        });
        for (ClientHandler client : clients) {
            client.sendMessage(roomList.toString());
        }
    }

    public static synchronized void updateUserLists(ChatRoom room) {
        if (room != null) {
            StringBuilder userList = new StringBuilder("USERLIST|");
            room.getUsers().forEach(user -> userList.append(user).append("|"));
            for (ClientHandler client : room.getClients()) {
                client.sendMessage(userList.toString());
            }
        }
    }
    
    public static synchronized ChatRoom getChatRoom(String roomName) {
        return chatRooms.computeIfAbsent(roomName, ChatRoom::new);
    }

    public static synchronized void removeClient(ClientHandler client) {
        clients.remove(client);
        System.out.println("Client disconnected: " + client.getUsername());
    }

    public static synchronized Set<String> getAllUsernames() {
        Set<String> usernames = new HashSet<>();
        for (ClientHandler client : clients) {
            usernames.add(client.getUsername());
        }
        return usernames;
    }

    public static synchronized Map<String, ChatRoom> getChatRooms() {
        return chatRooms;
    }
}

class ClientHandler implements Runnable {
    private Socket socket;
    private PrintWriter output;
    private BufferedReader input;
    private String username;
    private ChatRoom currentRoom;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }
    
    public String getUsername() {
        return username;
    }
    
    @Override
    public void run() {
        try {
            input = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            
            username = input.readLine();
            
            if (username == null || username.trim().isEmpty()) {
                output.println("ERROR|Username cannot be empty");
                closeConnection();
                return;
            }
            
            output.println("MESSAGE|Welcome " + username + "! Type '/help' for commands.");
            
            PrivateChat.registerUser(username, this);
            
            String message;
            while ((message = input.readLine()) != null) {
                processMessage(message);
            }
        } catch (IOException e) {
            System.err.println("Error handling client " + username + ": " + e.getMessage());
        } finally {
            closeConnection();
        }
    }
    
    private void processMessage(String message) {
        try {
            if (message.startsWith("/join ")) {
                String roomName = message.substring(6).trim();
                if (!roomName.isEmpty()) {
                    joinRoom(roomName);
                } else {
                    sendMessage("ERROR|Room name cannot be empty");
                }
            } else if (message.startsWith("/pm ")) {
                String[] parts = message.split(" ", 3);
                if (parts.length == 3) {
                    String recipient = parts[1];
                    String privateMessage = parts[2];
                    boolean sent = PrivateChat.sendMessage(recipient, username, privateMessage);
                    if (!sent) {
                        sendMessage("ERROR|User " + recipient + " not found or offline");
                    }
                } else {
                    sendMessage("ERROR|Invalid format. Use: /pm username message");
                }
            } else if (message.equals("/help")) {
                sendMessage("MESSAGE|Available commands:\n" +
                        "/join roomName - Join a chat room\n" +
                        "/pm username message - Send private message\n" +
                        "/users - Show room users\n" +
                        "/allusers - Show all users\n" +
                        "/quit - Disconnect from server");
            } else if (message.equals("/users")) {
                if (currentRoom != null) {
                    ChatServer.updateUserLists(currentRoom);
                }
            } else if (message.equals("/allusers")) {
                StringBuilder userList = new StringBuilder("USERLIST|");
                ChatServer.getAllUsernames().forEach(user -> userList.append(user).append("|"));
                sendMessage(userList.toString());
            } else if (message.equals("/quit")) {
                sendMessage("MESSAGE|Goodbye!");
                closeConnection();
            } else {
                if (currentRoom != null) {
                    ChatServer.broadcast("MESSAGE|" + username + ": " + message, currentRoom, this);
                } else {
                    sendMessage("ERROR|You are not in any room. Use /join roomName");
                }
            }
        } catch (Exception e) {
            sendMessage("ERROR|Error processing your message: " + e.getMessage());
        }
    }
    
    public void joinRoom(String roomName) {
        if (currentRoom != null) {
            currentRoom.removeClient(this);
            ChatServer.broadcast("MESSAGE|" + username + " left the room", currentRoom, this);
            ChatServer.updateUserLists(currentRoom);
        }
        currentRoom = ChatServer.getChatRoom(roomName);
        currentRoom.addClient(this);
        sendMessage("CLEAR|");
        sendMessage("JOINED|" + roomName);
        ChatServer.broadcast("MESSAGE|" + username + " joined the room", currentRoom, this);
        ChatServer.updateUserLists(currentRoom);
        ChatServer.updateRoomLists();
    }
    
    public void sendMessage(String message) {
        output.println(message);
    }
    
    private void closeConnection() {
        try {
            if (currentRoom != null) {
                currentRoom.removeClient(this);
                ChatServer.broadcast("MESSAGE|" + username + " left the room", currentRoom, this);
                ChatServer.updateUserLists(currentRoom);
            }
            PrivateChat.unregisterUser(username);
            ChatServer.removeClient(this);
            ChatServer.updateRoomLists();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }
}

class ChatRoom {
    private String name;
    private Set<ClientHandler> clients = new HashSet<>();
    
    public ChatRoom(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    public synchronized void addClient(ClientHandler client) {
        clients.add(client);
    }
    
    public synchronized void removeClient(ClientHandler client) {
        clients.remove(client);
        if (clients.isEmpty()) {
            ChatServer.getChatRooms().remove(name);
        }
    }
    
    public synchronized void broadcastMessage(String message, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) {
                client.sendMessage(message);
            }
        }
    }
    
    public synchronized Set<String> getUsers() {
        Set<String> usernames = new HashSet<>();
        for (ClientHandler client : clients) {
            usernames.add(client.getUsername());
        }
        return usernames;
    }
    
    public synchronized Set<ClientHandler> getClients() {
        return new HashSet<>(clients);
    }
}

class PrivateChat {
    private static Map<String, ClientHandler> users = new HashMap<>();
    
    public static synchronized void registerUser(String username, ClientHandler client) {
        users.put(username, client);
    }
    
    public static synchronized void unregisterUser(String username) {
        users.remove(username);
    }
    
    public static synchronized boolean sendMessage(String recipient, String sender, String message) {
        ClientHandler client = users.get(recipient);
        if (client != null) {
            client.sendMessage("PRIVATE|" + sender + ": " + message);
            return true;
        }
        return false;
    }
}