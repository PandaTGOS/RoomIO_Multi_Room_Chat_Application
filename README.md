# RoomIO_Multi_Room_Chat_Application


## Project Title and Description

**RoomIO_Multi_Room_Chat_Application** is a lightweight, multi-room chat application built using Java. It provides a range of features such as private messaging, room handling, abstraction, and more.

This project consists of two main components: **ChatClientSwing** and **ChatServer**. The **ChatClientSwing** is a graphical user interface for the chat client that allows users to connect to the server, join rooms, send messages, and more. The **ChatServer** is the back-end server that handles client connections, manages rooms, and broadcasts messages to all connected clients.

The core functionality of this application is to enable users to communicate with each other in real-time, regardless of their location. It achieves this by creating a virtual chat room where users can join, leave, and send messages to each other. The application also supports private messaging, allowing users to communicate one-on-one with other users.


## Key Features

1. **Multi-room chat functionality**: Users can join multiple chat rooms and communicate with other users in real-time.
2. **Private messaging**: Users can send and receive private messages from other users.
3. **Abstraction**: The application abstracts away the underlying network details, making it easy for users to connect and communicate with each other.
4. **Ease of use**: The graphical user interface is intuitive and user-friendly, making it easy for users to navigate and use the application.


## Installation and Setup

To install and set up **RoomIO_Multi_Room_Chat_Application**, follow these steps:

1. Download the latest version of the **ChatClientSwing** and **ChatServer** JAR files from the project repository.
2. Extract the JAR files to a location of your choice.
3. Open a terminal or command prompt and navigate to the extracted directory.
4. Run the **ChatServer** by executing the following command:
```java
java ChatServer
```
5. Open a new terminal or command prompt window and run the **ChatClientSwing** by executing the following command:
```java
java ChatClientSwing
```
6. Follow the prompts to connect to the server, join rooms, and start chatting.
