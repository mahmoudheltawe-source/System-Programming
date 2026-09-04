#pragma once

#include <sstream>
#include <string>
#include <vector>
#include "Order.h"
#include "Customer.h"
#include "Volunteer.h"

using std::string;
using std::vector;

class BaseAction;

class WareHouse {
    public:
        WareHouse(const string &configFilePath);
        WareHouse(const WareHouse &other);
        ~WareHouse();
        WareHouse &operator=(const WareHouse &other);
        WareHouse(WareHouse &&other) noexcept;
        WareHouse &operator=(WareHouse &&other) noexcept;

        void start();
        void processInput(const string &userInput);
        void processRestore(std::istringstream &is);
        void processBackup(std::istringstream &is);
        void processVolunteerStatus(std::istringstream &is);
        void processCustomerStatus(std::istringstream &is);
        void processOrderStatus(std::istringstream &is);
        void processLog(std::istringstream &is);
        void processClose(std::istringstream &is);
        void processStep(std::istringstream &is);
        void processCustomer(std::istringstream &is);
        void processOrder(std::istringstream &is);

        void addOrder(Order* order);
        void addCustomer(Customer* customer);
        void addAction(BaseAction* action);
        Customer &getCustomer(int customerId) const;
        Volunteer &getVolunteer(int volunteerId) const;
        Order &getOrder(int orderId) const;
        const vector<BaseAction*> &getActions() const;
        const vector<Volunteer*> &getVolunteers() const;
        const vector<Order*> &getPendingOrders() const;
        const vector<Order*> &getInProcessOrders() const;
        const vector<Order*> &getCompletedOrders() const;
        const vector<Customer*> &getCustomers() const;
        int getSimulateTimeOfWorking() const;
        void printAllOrdersStatus();
        int getNextOrderId() const;
        int getNextCustomerId() const;
        void increaseSimulateTimeOfWorking();
        bool isDummyVolunteer(const Volunteer& volunteer) const;
        bool isDummyCustomer(const Customer& customer) const;
        bool isDummyOrder(const Order& order) const;
        void removeFromPendingOrders(Order* order);
        void removeFromInProcessOrders(Order* order);
        void removeFromCompletedOrders(Order* order);
        void removeFromCustomers(Customer* customer);
        void removeFromVolunteers(Volunteer* volunteer);
        void addOrderToInProcessOrders(Order* order);
        void addOrderToCompletedOrders(Order* order);
        void assignOrdersFromPendingToIsProcessOrders();
        void performStep();
        void volunteersFinishedOrder();
        void deleteFinishedVolunteers();
        void close();
        void open();
        void clear();

    private:
        void copyFrom(const WareHouse &other);

        bool isOpen;
        vector<BaseAction*> actionsLog;
        vector<Volunteer*> volunteers;
        vector<Order*> pendingOrders;
        vector<Order*> inProcessOrders;
        vector<Order*> completedOrders;
        vector<Customer*> customers;
        int customerCounter;
        int volunteerCounter;
        int orderCounter;
        int simulateTimeOfWorking;
};
