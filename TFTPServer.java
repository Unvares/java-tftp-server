package assignment3;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.channels.FileChannel;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class TFTPServer {
  public static final int TFTPPORT = 4970;
  public static final int BUFSIZE = 516;
  public static final String READDIR = "test_a3/";
  public static final String WRITEDIR = "test_a3/";
  // OP codes
  public static final int OP_RRQ = 1;
  public static final int OP_WRQ = 2;
  public static final int OP_DAT = 3;
  public static final int OP_ACK = 4;
  public static final int OP_ERR = 5;

  public static void main(String[] args) {
    if (args.length > 0) {
      System.err.printf("usage: java %s\n", TFTPServer.class.getCanonicalName());
      System.exit(1);
    }
    // Starting the server
    try {
      TFTPServer server = new TFTPServer();
      server.start();
    } catch (SocketException e) {
      e.printStackTrace();
    }
  }

  private void start() throws SocketException {
    byte[] buf = new byte[BUFSIZE];

    // Create socket
    DatagramSocket socket = new DatagramSocket(null);

    // Create local bind point
    SocketAddress localBindPoint = new InetSocketAddress(TFTPPORT);
    socket.bind(localBindPoint);

    System.out.printf("Listening at port %d for new requests\n", TFTPPORT);

    // Loop to handle client requests
    while (true) {
      final InetSocketAddress clientAddress = receiveFrom(socket, buf);

      // If clientAddress is null, an error occurred in receiveFrom()
      if (clientAddress == null)
        continue;

      final StringBuffer requestedFile = new StringBuffer();
      final int reqtype = ParseRQ(buf, requestedFile);

      new Thread() {
        public void run() {
          try {
            DatagramSocket sendSocket = new DatagramSocket(0);

            // Connect to client
            sendSocket.connect(clientAddress);

            System.out.printf("%s request for %s from %s using port %d\n",
                (reqtype == OP_RRQ) ? "Read" : "Write",
                requestedFile.toString(),
                clientAddress.getHostName(),
                clientAddress.getPort()
            );

            // Read request
            if (reqtype == OP_RRQ) {
              requestedFile.insert(0, READDIR);
              HandleRQ(sendSocket, requestedFile.toString(), OP_RRQ);
            }
            // Write request
            else {
              requestedFile.insert(0, WRITEDIR);
              HandleRQ(sendSocket, requestedFile.toString(), OP_WRQ);
            }
            sendSocket.close();
          } catch (SocketException e) {
            e.printStackTrace();
          }
        }
      }.start();
    }
  }

  /**
   * Reads the first block of data, i.e., the request for an action (read or
   * write).
   * 
   * @param socket (socket to read from)
   * @param buf    (where to store the read data)
   * @return socketAddress (the socket address of the client)
   */
  private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) {
    // Create datagram packet
    DatagramPacket packet = new DatagramPacket(buf, buf.length);

    // Receive packet
    try {
      socket.receive(packet);
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }

    // Get client address and port from the packet
    InetSocketAddress socketAddress = (InetSocketAddress) packet.getSocketAddress();

    return socketAddress;
  }

  /**
   * Parses the request in buf to retrieve the type of request and requestedFile
   * 
   * @param buf           (received request)
   * @param requestedFile (name of file to read/write)
   * @return opcode (request type: RRQ or WRQ)
   */
  private int ParseRQ(byte[] buf, StringBuffer requestedFile) {
    // See "TFTP Formats" in TFTP specification for the RRQ/WRQ request contents
    int opcode = buf[0];
    opcode += buf[1];
    if (opcode == OP_RRQ || opcode == OP_WRQ) {
      for (int i = 2; i < BUFSIZE - 1; i++) {
        if (buf[i] == 0) {
          break;
        } else {
          String part = new String(buf, i, 1, StandardCharsets.UTF_8);
          requestedFile.append(part);
        }
      }
    }
    return opcode;
  }

  /**
   * Handles RRQ and WRQ requests
   * 
   * @param sendSocket    (socket used to send/receive packets)
   * @param requestedFile (name of file to read/write)
   * @param opcode        (RRQ or WRQ)
   */
  private void HandleRQ(DatagramSocket sendSocket, String requestedFile, int opcode) {
    if (opcode == OP_RRQ) {
      // Open the file
      Path filePath = Paths.get(requestedFile);
      try (FileChannel fileChannel = FileChannel.open(filePath)) {
        ByteBuffer buffer = ByteBuffer.allocate(BUFSIZE);
        int bytesRead = fileChannel.read(buffer);
        short blockNumber = 1;

        while (bytesRead != -1) {
          buffer.flip();
          byte[] fileData = new byte[bytesRead];
          buffer.get(fileData);

          boolean result = send_DATA_receive_ACK(sendSocket, fileData, blockNumber);
          if (!result) {
            System.err.println("Error sending data or receiving acknowledgment");
            send_ERR();
            return;
          }

          buffer.clear();
          bytesRead = fileChannel.read(buffer);
          blockNumber++;
        }
      } catch (IOException e) {
        System.err.println("Error reading file: " + e.getMessage());
        send_ERR();
      }
    } else if (opcode == OP_WRQ) {
      File file = new File(requestedFile);
      if (file.exists()) {
        System.out.println("The file is already present in the server. Deleting it now.");
        file.delete();
      }
      try {
        file.createNewFile();
        FileOutputStream writer = new FileOutputStream(file);

        // loops until all packets are received
        short blockNumber = 0;
        while (true) {
          DatagramPacket dataPacket = receive_DATA_send_ACK(sendSocket, blockNumber);

          if (dataPacket != null) {
            byte[] data = dataPacket.getData();
            writer.write(data, 4, dataPacket.getLength() - 4); // first 4 bytes are opcode and block number

            blockNumber++;

            if (dataPacket.getLength() - 4 < 512) {
              System.out.println("Last packet recived");
              sendSocket.send(getAckPacket(blockNumber));
              break;
            }
          } else {
            System.out.println("Something went wrong with reciving data!");
            file.delete();
            break;
          }
        }
        writer.close();
      } catch (IOException e) {
        file.delete();
        e.printStackTrace();
      }
    } else {
      System.err.println("Invalid request. Sending an error packet.");
      // See "TFTP Formats" in TFTP specification for the ERROR packet contents
      send_ERR();
      return;
    }
  }

  private boolean send_DATA_receive_ACK(DatagramSocket sendSocket, byte[] fileData, short blockNumber) {
    try {
      // Create a buffer for the data packet
      byte[] dataBuffer = new byte[4 + fileData.length];

      // Set the opcode to DATA
      dataBuffer[0] = 0;
      dataBuffer[1] = OP_DAT;
      dataBuffer[2] = (byte) (blockNumber >> 8);
      dataBuffer[3] = (byte) blockNumber;

      // Copy the file data into the buffer
      System.arraycopy(fileData, 0, dataBuffer, 4, fileData.length);

      // Create and send the data packet
      DatagramPacket dataPacket = new DatagramPacket(
        dataBuffer,
        dataBuffer.length,
        sendSocket.getInetAddress(),
        sendSocket.getPort()
      );
      sendSocket.send(dataPacket);

      // Create a buffer for the acknowledgment packet
      byte[] ackBuffer = new byte[4];
      DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);

      // Receive the acknowledgment packet
      sendSocket.receive(ackPacket);

      // Create a ByteBuffer wrapping ackBuffer
      ByteBuffer wrap = ByteBuffer.wrap(ackBuffer);

      // Read the opcode as a short
      short opcode = wrap.getShort();

      // Check if the opcode is right
      if (opcode == OP_ACK) {
        return true;
      } else {
        return false;
      }
    } catch (IOException e) {
      System.err.println("Error sending data or receiving acknowledgment: " + e.getMessage());
      send_ERR();
      return false;
    }
  }

  /**
   * Gives back the data if it recives any.
   * 
   * @param sendSocket The sender Socket
   * @param blockNumber The current block number.
   * @return The recived data if its correct block number.
   */
  private DatagramPacket receive_DATA_send_ACK(DatagramSocket sendSocket, short blockNumber) {
    byte[] buf = new byte[BUFSIZE];
    DatagramPacket dataPacket = new DatagramPacket(buf, buf.length);
    int retryCounter = 0;
    DatagramPacket ackPacket = getAckPacket(blockNumber);
    
    try {
      if (retryCounter == 3) {
        System.out.println("Failed to many times");
        return null;

      } else {

        
        sendSocket.send(ackPacket);
        sendSocket.setSoTimeout(10000); //Time out occures in the packet is not recevied in 10s 
        sendSocket.receive(dataPacket);
        short packetBlockNumber = retrieveBlockNumber(dataPacket);
        System.out.println("Recived block number " + packetBlockNumber);
  
        if (packetBlockNumber == ++blockNumber) {
          return dataPacket;
        } else {
          System.out.println("Incorrect packet");
          retryCounter = 0;
          throw new SocketTimeoutException();
        }
      }
     
    }catch (SocketTimeoutException se) {
      System.out.println("Timedout! Resending!");
      try {
        sendSocket.send(ackPacket);
      } catch (IOException e) {
        System.err.println("Error in resending!");
        e.printStackTrace();
      }

    } catch (IOException e) {
      System.err.println("Something went wrong with reciving data!");
      e.printStackTrace();
    }
    return null;
  }

  private void send_ERR() {
    // To be implemented
  }

  /**
   * Create the acknowledgement DatagramPacket.
   * 
   * @param blockNum The block number.
   * @return The packet.
   */
  private DatagramPacket getAckPacket(short blockNum) {
    byte[] acknowledgementPacket = new byte[4];
    acknowledgementPacket[0] = (byte) (OP_ACK >> 8);
    acknowledgementPacket[1] = (byte) OP_ACK;
    acknowledgementPacket[2] = (byte) (blockNum >> 8);
    acknowledgementPacket[3] = (byte) blockNum;

    return new DatagramPacket(acknowledgementPacket, 4);
  }
  
  /**
   * Get the block number in short form.
   * 
   * @param dataPacket recived data
   * @return block number
   */
  private short retrieveBlockNumber(DatagramPacket dataPacket) {
    ByteBuffer wrapper = ByteBuffer.wrap(dataPacket.getData());
    wrapper.getShort();
    return wrapper.getShort(); // block number is stored in the second two bytes
  }
}
