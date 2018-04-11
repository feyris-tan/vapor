# Vapor

This is a simple proof-of-concept application for a Java Servlet container which serves the EasyRPG Player, with the game assets stored in a Database.

An importer tool (written in C#) is supplied.

## How to use:

 1. Create a database schema and execute setup.sql in it. I have tested it with MariaDB 10 - but I guess it'll work with other databases too.
 2. Inside the servlet container, create a resource named "jdbc/vaporDb" which points to the database.
 3. Build the vaporimport Application, and use it to import the RTP.
 4. Import your game(-s) with vaporimport.
 5. Build the vaporJava Application and deploy it to your servlet container.
 6. Point your web browser towards the application URL, and start playing.