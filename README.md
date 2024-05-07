# Set card game- Threads programing on JAVA
# Project Overview
This project implements a simplified version of the game "Set" in Java, focusing on concurrent programming concepts and unit testing. The game involves finding combinations of three cards that constitute a "legal set" based on their features. The project provides a framework for game logic, player interactions, and graphical user interface, with the main goal being the implementation of game logic components **implementing programming with threads on java.**

# Features
* Generates a deck of 81 cards with various features: color, number, shape, and shading.
* Players aim to find sets of three cards where each feature is either all the same or all different.
* Supports both human and non-human players.
* Includes graphical representation and keyboard input handling.
* Utilizes Java threads and synchronization mechanisms for concurrent gameplay.

# Usage
To run the project:
1. Clone the repository to your local machine.
2. Ensure you have Maven installed.
3. Navigate to the project directory in your terminal.
4. Compile the project using Maven: ``mvn compile``
5. Run the project: ``mvn exec:java``
6. Follow the on-screen instructions to interact with the game. Human players can use the designated keys on the keyboard to place or remove tokens from cards. Non-human players are simulated by threads that produce random key presses.

# Notes
Ensure you have Java and Maven installed on your system.

# Author
Amnon Abaev
