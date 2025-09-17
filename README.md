# Flight Reservations App
A full-stack, console-based application for searching, booking, and managing flight reservations.

**Note:**
This project was part of the coursework for CSE 344 at the University of Washington. The majority of the framework was provided by the instructors.

**Features:**
- Secure user registration/login and authentication with salted and hashed password storage
- Find direct and multi-stop itineraries based on origin, destination, and date, ranked by duration and cost.
- Real-time seat availability to prevent overbooking
- Secure payment system that validates user balances and processes transactions atomically
- View all reservations on account with details of itineraries and payment status

**Usage:**
- **Create new user:** create <username> <password> <initial_balance>
- **Login:** login <username> <password>
- **Search:** search <origin city> <destination city> <direct> <day> <num itineraries>
- **Book:** book <itinerary id>
- **Pay:** pay <reservation id>
- **Reservations:** reservations

Run by starting the ssh tunnel:
$ ./pgtunnel.sh
and run:
pgtunnel> mvn compile exec:java
