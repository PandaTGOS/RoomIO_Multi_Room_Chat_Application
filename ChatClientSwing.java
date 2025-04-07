import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.net.Socket;
import java.util.Enumeration;

public class ChatClientSwing {
    private JFrame frame;
    private JTextArea chatArea;
    private JTextField inputField;
    private JList<String> userList;
    private JList<String> roomList;
    private BufferedReader in;
    private PrintWriter out;
    private Socket socket;
    private String username;
    private String currentRoom = "Lobby";
    private boolean showAllUsers = false;
    private JButton toggleUsersButton;
    private JPanel sidePanel;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatClientSwing().createAndShowGUI());
    }

    private void createAndShowGUI() {
        frame = new JFrame("Chat Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 500);
        frame.setLayout(new BorderLayout());

        // Chat area
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        JScrollPane chatScroll = new JScrollPane(chatArea);
        frame.add(chatScroll, BorderLayout.CENTER);

        // Input panel
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        inputField.addActionListener(e -> sendMessage());
        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sendMessage());
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        frame.add(inputPanel, BorderLayout.SOUTH);

        // Side panel with fixed size
        sidePanel = new JPanel();
        sidePanel.setLayout(new BoxLayout(sidePanel, BoxLayout.Y_AXIS));
        sidePanel.setPreferredSize(new Dimension(150, 500));

        // Room list
        roomList = new JList<>();
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedRoom = roomList.getSelectedValue();
                if (selectedRoom != null && !selectedRoom.equals(currentRoom)) {
                    out.println("/join " + selectedRoom);
                }
            }
        });
        JScrollPane roomScroll = new JScrollPane(roomList);
        roomScroll.setPreferredSize(new Dimension(150, 200));
        JPanel roomPanel = new JPanel(new BorderLayout());
        roomPanel.add(new JLabel("Rooms:"), BorderLayout.NORTH);
        roomPanel.add(roomScroll, BorderLayout.CENTER);
        sidePanel.add(roomPanel);

        // User list
        userList = new JList<>();
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    String selectedUser = userList.getSelectedValue();
                    if (selectedUser != null && !selectedUser.equals(username)) {
                        showPrivateMessageDialog(selectedUser);
                    }
                }
            }
        });
        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setPreferredSize(new Dimension(150, 200));
        JPanel userPanel = new JPanel(new BorderLayout());
        userPanel.add(new JLabel("Users:"), BorderLayout.NORTH);
        userPanel.add(userScroll, BorderLayout.CENTER);
        sidePanel.add(userPanel);

        frame.add(sidePanel, BorderLayout.EAST);

        // Button panel
        JPanel buttonPanel = new JPanel(new GridLayout(5, 1));
        buttonPanel.setPreferredSize(new Dimension(100, 500));
        
        JButton joinButton = new JButton("Join Room");
        joinButton.addActionListener(e -> showJoinRoomDialog());
        buttonPanel.add(joinButton);

        JButton pmButton = new JButton("Private Msg");
        pmButton.addActionListener(e -> showPrivateMessageDialog(null));
        buttonPanel.add(pmButton);

        toggleUsersButton = new JButton("Show All Users");
        toggleUsersButton.addActionListener(e -> {
            showAllUsers = !showAllUsers;
            updateUserListButtonText();
            out.println(showAllUsers ? "/allusers" : "/users");
        });
        buttonPanel.add(toggleUsersButton);

        JButton helpButton = new JButton("Help");
        helpButton.addActionListener(e -> out.println("/help"));
        buttonPanel.add(helpButton);

        JButton quitButton = new JButton("Quit");
        quitButton.addActionListener(e -> {
            out.println("/quit");
            frame.dispose();
        });
        buttonPanel.add(quitButton);

        frame.add(buttonPanel, BorderLayout.WEST);

        showLoginDialog();
        frame.setVisible(true);
    }

    private void updateUserListButtonText() {
        toggleUsersButton.setText(showAllUsers ? "Show Room Users" : "Show All Users");
    }

    private void showLoginDialog() {
        JPanel panel = new JPanel(new GridLayout(3, 2));
        JTextField ipField = new JTextField(getNetworkIPAddress());
        JTextField portField = new JTextField("12345");
        JTextField nameField = new JTextField();

        panel.add(new JLabel("Server IP:"));
        panel.add(ipField);
        panel.add(new JLabel("Port:"));
        panel.add(portField);
        panel.add(new JLabel("Username:"));
        panel.add(nameField);

        int result = JOptionPane.showConfirmDialog(
            frame, panel, "Connect to Server", 
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            try {
                String server = ipField.getText().trim();
                int port = Integer.parseInt(portField.getText().trim());
                username = nameField.getText().trim();
                
                if (username.isEmpty()) {
                    JOptionPane.showMessageDialog(frame, 
                        "Username cannot be empty", "Error", JOptionPane.ERROR_MESSAGE);
                    showLoginDialog();
                    return;
                }
                
                connectToServer(server, port);
                frame.setTitle("Chat Client - " + username);
                out.println("/join " + currentRoom);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(frame, 
                    "Invalid port number", "Error", JOptionPane.ERROR_MESSAGE);
                showLoginDialog();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(frame, 
                    "Could not connect to server: " + e.getMessage(), 
                    "Error", JOptionPane.ERROR_MESSAGE);
                showLoginDialog();
            }
        } else {
            System.exit(0);
        }
    }

    private String getNetworkIPAddress() {
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

    private void connectToServer(String server, int port) throws IOException {
        socket = new Socket(server, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

        out.println(username);

        new Thread(() -> {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    handleServerMessage(message);
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> 
                    JOptionPane.showMessageDialog(frame, 
                        "Lost connection to server", "Error", JOptionPane.ERROR_MESSAGE));
            } finally {
                SwingUtilities.invokeLater(() -> frame.dispose());
            }
        }).start();
    }

    private void handleServerMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            if (message.startsWith("ERROR|")) {
                JOptionPane.showMessageDialog(frame, 
                    message.substring(6), "Error", JOptionPane.ERROR_MESSAGE);
            } 
            else if (message.startsWith("PRIVATE|")) {
                chatArea.append("[PM] " + message.substring(8) + "\n");
            } 
            else if (message.startsWith("MESSAGE|")) {
                String msgContent = message.substring(8);
                if (!msgContent.startsWith(username + ": ")) {
                    chatArea.append(msgContent + "\n");
                }
            }
            else if (message.startsWith("CLEAR|")) {
                chatArea.setText("");
            }
            else if (message.startsWith("ROOMLIST|")) {
                String[] rooms = message.substring(9).split("\\|");
                roomList.setListData(rooms);
            }
            else if (message.startsWith("USERLIST|")) {
                String[] users = message.substring(9).split("\\|");
                userList.setListData(users);
            }
            else if (message.startsWith("JOINED|")) {
                currentRoom = message.substring(7);
                frame.setTitle("Chat Client - " + username + " (" + currentRoom + ")");
                chatArea.append("Joined room: " + currentRoom + "\n");
            }
            else {
                chatArea.append(message + "\n");
            }
        });
    }

    private void sendMessage() {
        String message = inputField.getText().trim();
        if (!message.isEmpty()) {
            chatArea.append("You: " + message + "\n");
            out.println(message);
            inputField.setText("");
        }
    }

    private void showJoinRoomDialog() {
        String roomName = JOptionPane.showInputDialog(frame, 
            "Enter room name to join or create:", "Join Room", JOptionPane.PLAIN_MESSAGE);
        if (roomName != null && !roomName.trim().isEmpty()) {
            out.println("/join " + roomName.trim());
        }
    }

    private void showPrivateMessageDialog(String recipient) {
        String user = recipient;
        if (user == null) {
            user = JOptionPane.showInputDialog(frame, 
                "Enter recipient username:", "Private Message", JOptionPane.PLAIN_MESSAGE);
            if (user == null || user.trim().isEmpty()) {
                return;
            }
            user = user.trim();
        }
        
        String message = JOptionPane.showInputDialog(frame, 
            "Enter your message to " + user + ":", 
            "Private Message", JOptionPane.PLAIN_MESSAGE);
        if (message != null && !message.trim().isEmpty()) {
            out.println("/pm " + user + " " + message.trim());
        }
    }
}