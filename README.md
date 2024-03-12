# README
## Project Dependencies
This project requires the following dependencies to be installed:
- Java Development Kit (JDK): This is used to compile and run the Java server.
- pip3: This is a package installer for Python, used to install the necessary Python packages.
- python3: The Python interpreter, version 3, is required to run the Python scripts.

## Python Dependencies
To install the Python dependencies, follow these steps:
- Navigate to the project directory in your console.
- Run the command `pip install -r requirements.txt` or `pip3 install -r requirements.txt`. This will install all the Python dependencies listed in the requirements.txt file.

## Running the Server
To run the server, follow these steps:
- Navigate to the project directory in your console.
- Run the command `javac TFTPServer.java`. This will compile the server and generate a TFTPServer.class file in the same directory. This class file is executable by the Java Virtual Machine (JVM).
- Run the command `java -cp .. assignment3.TFTPServer`. This command runs the server. The `-cp ..` argument is necessary because without it, the JVM will attempt to find the class in the root directory. By setting it relative to the current open directory, we ensure the JVM can find the class file.

### Alternative Method
If you're using Visual Studio Code (VSCode), you can simply click the "run" button located in the upper right corner of the interface to run the server.

## Running Tests
To run the tests, follow these steps:
- Navigate to the test_a3 directory in your console.
- Run the command `python -m pytest` or `python3 -m pytest`.

Please note that the `test_GFileNotExists` test is expected to fail. This is intentional, as the test is related to VG-Problem 4, and we have only implemented solutions for Problem 1 and VG-Problem 2.
