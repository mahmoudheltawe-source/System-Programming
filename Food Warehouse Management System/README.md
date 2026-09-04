# Food Warehouse Management System

This project is a C++ simulation of a food warehouse. Customers can place food-package orders, collector volunteers prepare the orders, and driver volunteers deliver them. The program follows the Assignment 1 requirements for SPL: object-oriented design, inheritance, dynamic allocation, cloning, backup/restore, and Rule of 5 support where needed.

## Project Structure

```txt
include/        Header files
src/            C++ implementation files
tests/          Example configuration and expected-output reference
bin/            Compiled object files and executable, created by make
Makefile        Build commands
README.md       This file
```

## Build

Open a terminal in the project folder, then run:

```bash
make
```

This creates the executable:

```txt
bin/warehouse
```

To remove compiled files:

```bash
make clean
```

## Run

Run the program with a configuration file:

```bash
bin/warehouse tests/input_file.txt
```

You should see:

```txt
Warehouse is open!
```

After that, type commands in the same terminal. Press Enter after each command.

Example:

```txt
order 3
step 1
orderStatus 0
log
close
```

## Configuration File Format

The configuration file describes the starting customers and volunteers.

Customer format:

```txt
customer <name> <soldier/civilian> <distance> <max_orders>
```

Volunteer formats:

```txt
volunteer <name> collector <cool_down>
volunteer <name> limited_collector <cool_down> <max_orders>
volunteer <name> driver <max_distance> <distance_per_step>
volunteer <name> limited_driver <max_distance> <distance_per_step> <max_orders>
```

The included config file is:

```txt
tests/input_file.txt
```

## Program Commands

Use these commands after the program prints `Warehouse is open!`.

```txt
order <customer_id>
customer <name> <soldier/civilian> <distance> <max_orders>
step <number_of_steps>
orderStatus <order_id>
customerStatus <customer_id>
volunteerStatus <volunteer_id>
log
backup
restore
close
```

## Automatic Test Example

Instead of typing commands one by one, you can pipe them into the program:

```bash
printf 'order 3\norder 4\norder 5\norder 5\ncustomerStatus 5\nstep 1\nvolunteerStatus 0\norderStatus 0\nstep 1\norderStatus 0\norderStatus 1\nbackup\nstep 2\norderStatus 2\nrestore\norderStatus 2\nlog\nclose\n' | bin/warehouse tests/input_file.txt
```

Expected output:

```txt
Warehouse is open!
Error: Cannot place this order
CustomerID: 5
OrderId: 2
OrderStatus: Pending
numOrdersLeft: 0
Error: Volunteer doesn't exist
OrderId: 0
OrderStatus: Collecting
CustomerID: 3
Collector: 0
Driver: None
OrderId: 0
OrderStatus: Completed
CustomerID: 3
Collector: 0
Driver: 3
OrderId: 1
OrderStatus: Collecting
CustomerID: 4
Collector: 1
Driver: None
OrderId: 2
OrderStatus: Delivering
CustomerID: 5
Collector: 2
Driver: 3
OrderId: 2
OrderStatus: Collecting
CustomerID: 5
Collector: 2
Driver: None
order 3 COMPLETED
order 4 COMPLETED
order 5 COMPLETED
order 5 ERROR
customerStatus 5 COMPLETED
step 1 COMPLETED
volunteerStatus 0 ERROR
orderStatus 0 COMPLETED
step 1 COMPLETED
orderStatus 0 COMPLETED
orderStatus 1 COMPLETED
restore COMPLETED
orderStatus 2 COMPLETED
OrderID: 0 , CustomerID: 3 , Status: Completed
OrderID: 1 , CustomerID: 4 , Status: Collecting
OrderID: 2 , CustomerID: 5 , Status: Collecting
```

## Memory Check

On a computer with Valgrind installed, run:

```bash
valgrind --leak-check=full --show-reachable=yes bin/warehouse tests/input_file.txt
```

Then type commands and finish with:

```txt
close
```

The important expected Valgrind lines are:

```txt
All heap blocks were freed -- no leaks are possible
ERROR SUMMARY: 0 errors
```

## Submission Note

Before creating the final zip file, run:

```bash
make clean
```

The assignment asks for the `bin/` directory to be submitted empty.
