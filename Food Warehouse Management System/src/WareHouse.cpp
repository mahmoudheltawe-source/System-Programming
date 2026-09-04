#include "WareHouse.h"
#include "Action.h"

#include <algorithm>
#include <cctype>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <utility>

namespace {
    string toLowerCopy(string value) {
        std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
            return static_cast<char>(std::tolower(c));
        });
        return value;
    }

    string withoutComments(const string &line) {
        string result = line;
        size_t hashPos = result.find('#');
        size_t slashPos = result.find("//");
        size_t commentPos = string::npos;

        if (hashPos != string::npos && slashPos != string::npos) {
            commentPos = std::min(hashPos, slashPos);
        } else if (hashPos != string::npos) {
            commentPos = hashPos;
        } else if (slashPos != string::npos) {
            commentPos = slashPos;
        }

        if (commentPos != string::npos) {
            result.erase(commentPos);
        }

        return result;
    }

    string orderStatusToString(OrderStatus status) {
        if (status == OrderStatus::PENDING) {
            return "Pending";
        }
        if (status == OrderStatus::COLLECTING) {
            return "Collecting";
        }
        if (status == OrderStatus::DELIVERING) {
            return "Delivering";
        }
        return "Completed";
    }

    template <typename T>
    void deleteVector(vector<T*> &items) {
        for (T *item : items) {
            delete item;
        }
        items.clear();
    }

    template <typename T>
    bool detachPointer(vector<T*> &items, T *target) {
        typename vector<T*>::iterator it = std::find(items.begin(), items.end(), target);
        if (it == items.end()) {
            return false;
        }

        items.erase(it);
        return true;
    }
}

WareHouse::WareHouse(const string& configFilePath):
    isOpen(false),
    actionsLog(),
    volunteers(),
    pendingOrders(),
    inProcessOrders(),
    completedOrders(),
    customers(),
    customerCounter(0),
    volunteerCounter(0),
    orderCounter(0),
    simulateTimeOfWorking(0) {

    std::ifstream configFile(configFilePath);
    if (!configFile.is_open()) {
        std::cerr << "Error: Unable to open configuration file" << std::endl;
        std::exit(EXIT_FAILURE);
    }

    string line;
    while (std::getline(configFile, line)) {
        std::istringstream lineStream(withoutComments(line));
        string recordType;
        if (!(lineStream >> recordType)) {
            continue;
        }

        recordType = toLowerCopy(recordType);
        if (recordType == "customer") {
            string customerName;
            string customerType;
            int distance;
            int maxOrders;

            if (!(lineStream >> customerName >> customerType >> distance >> maxOrders)) {
                continue;
            }

            customerType = toLowerCopy(customerType);
            if (customerType == "soldier") {
                customers.push_back(new SoldierCustomer(customerCounter++, customerName, distance, maxOrders));
            } else if (customerType == "civilian") {
                customers.push_back(new CivilianCustomer(customerCounter++, customerName, distance, maxOrders));
            }
        } else if (recordType == "volunteer") {
            string volunteerName;
            string volunteerRole;

            if (!(lineStream >> volunteerName >> volunteerRole)) {
                continue;
            }

            volunteerRole = toLowerCopy(volunteerRole);
            if (volunteerRole == "collector") {
                int coolDown;
                if (lineStream >> coolDown) {
                    volunteers.push_back(new CollectorVolunteer(volunteerCounter++, volunteerName, coolDown));
                }
            } else if (volunteerRole == "limited_collector" || volunteerRole == "limitedcollector") {
                int coolDown;
                int maxOrders;
                if (lineStream >> coolDown >> maxOrders) {
                    volunteers.push_back(new LimitedCollectorVolunteer(volunteerCounter++, volunteerName, coolDown, maxOrders));
                }
            } else if (volunteerRole == "driver") {
                int maxDistance;
                int distancePerStep;
                if (lineStream >> maxDistance >> distancePerStep) {
                    volunteers.push_back(new DriverVolunteer(volunteerCounter++, volunteerName, maxDistance, distancePerStep));
                }
            } else if (volunteerRole == "limited_driver" || volunteerRole == "limiteddriver") {
                int maxDistance;
                int distancePerStep;
                int maxOrders;
                if (lineStream >> maxDistance >> distancePerStep >> maxOrders) {
                    volunteers.push_back(new LimitedDriverVolunteer(volunteerCounter++, volunteerName, maxDistance, distancePerStep, maxOrders));
                }
            }
        }
    }
}

WareHouse::WareHouse(const WareHouse &other):
    isOpen(other.isOpen),
    actionsLog(),
    volunteers(),
    pendingOrders(),
    inProcessOrders(),
    completedOrders(),
    customers(),
    customerCounter(other.customerCounter),
    volunteerCounter(other.volunteerCounter),
    orderCounter(other.orderCounter),
    simulateTimeOfWorking(other.simulateTimeOfWorking) {
    copyFrom(other);
}

WareHouse::~WareHouse() {
    clear();
}

WareHouse &WareHouse::operator=(const WareHouse &other) {
    if (this != &other) {
        clear();
        isOpen = other.isOpen;
        customerCounter = other.customerCounter;
        volunteerCounter = other.volunteerCounter;
        orderCounter = other.orderCounter;
        simulateTimeOfWorking = other.simulateTimeOfWorking;
        copyFrom(other);
    }

    return *this;
}

WareHouse::WareHouse(WareHouse &&other) noexcept:
    isOpen(other.isOpen),
    actionsLog(std::move(other.actionsLog)),
    volunteers(std::move(other.volunteers)),
    pendingOrders(std::move(other.pendingOrders)),
    inProcessOrders(std::move(other.inProcessOrders)),
    completedOrders(std::move(other.completedOrders)),
    customers(std::move(other.customers)),
    customerCounter(other.customerCounter),
    volunteerCounter(other.volunteerCounter),
    orderCounter(other.orderCounter),
    simulateTimeOfWorking(other.simulateTimeOfWorking) {

    other.actionsLog.clear();
    other.volunteers.clear();
    other.pendingOrders.clear();
    other.inProcessOrders.clear();
    other.completedOrders.clear();
    other.customers.clear();
    other.isOpen = false;
    other.customerCounter = 0;
    other.volunteerCounter = 0;
    other.orderCounter = 0;
    other.simulateTimeOfWorking = 0;
}

WareHouse &WareHouse::operator=(WareHouse &&other) noexcept {
    if (this != &other) {
        clear();
        isOpen = other.isOpen;
        actionsLog = std::move(other.actionsLog);
        volunteers = std::move(other.volunteers);
        pendingOrders = std::move(other.pendingOrders);
        inProcessOrders = std::move(other.inProcessOrders);
        completedOrders = std::move(other.completedOrders);
        customers = std::move(other.customers);
        customerCounter = other.customerCounter;
        volunteerCounter = other.volunteerCounter;
        orderCounter = other.orderCounter;
        simulateTimeOfWorking = other.simulateTimeOfWorking;

        other.actionsLog.clear();
        other.volunteers.clear();
        other.pendingOrders.clear();
        other.inProcessOrders.clear();
        other.completedOrders.clear();
        other.customers.clear();
        other.isOpen = false;
        other.customerCounter = 0;
        other.volunteerCounter = 0;
        other.orderCounter = 0;
        other.simulateTimeOfWorking = 0;
    }

    return *this;
}

void WareHouse::copyFrom(const WareHouse &other) {
    for (const BaseAction *action : other.actionsLog) {
        actionsLog.push_back(action->clone());
    }
    for (const Volunteer *volunteer : other.volunteers) {
        volunteers.push_back(volunteer->clone());
    }
    for (const Order *order : other.pendingOrders) {
        pendingOrders.push_back(order->clone());
    }
    for (const Order *order : other.inProcessOrders) {
        inProcessOrders.push_back(order->clone());
    }
    for (const Order *order : other.completedOrders) {
        completedOrders.push_back(order->clone());
    }
    for (const Customer *customer : other.customers) {
        customers.push_back(customer->clone());
    }
}

void WareHouse::clear() {
    deleteVector(actionsLog);
    deleteVector(volunteers);
    deleteVector(pendingOrders);
    deleteVector(inProcessOrders);
    deleteVector(completedOrders);
    deleteVector(customers);
}

void WareHouse::start() {
    open();
    std::cout << "Warehouse is open!" << std::endl;

    string userInput;
    while (isOpen && std::getline(std::cin, userInput)) {
        processInput(userInput);
    }
}

void WareHouse::processInput(const string &userInput) {
    std::istringstream input(userInput);
    string actionType;
    input >> actionType;

    if (actionType == "order") {
        processOrder(input);
    } else if (actionType == "customer") {
        processCustomer(input);
    } else if (actionType == "step") {
        processStep(input);
    } else if (actionType == "close") {
        processClose(input);
    } else if (actionType == "log") {
        processLog(input);
    } else if (actionType == "orderStatus") {
        processOrderStatus(input);
    } else if (actionType == "customerStatus") {
        processCustomerStatus(input);
    } else if (actionType == "volunteerStatus") {
        processVolunteerStatus(input);
    } else if (actionType == "backup") {
        processBackup(input);
    } else if (actionType == "restore") {
        processRestore(input);
    }
}

void WareHouse::processOrder(std::istringstream &is) {
    int customerId;
    if (is >> customerId) {
        BaseAction *action = new AddOrder(customerId);
        action->act(*this);
        addAction(action);
    }
}

void WareHouse::processCustomer(std::istringstream &is) {
    string customerName;
    string customerType;
    int customerDistance;
    int customerMaxOrders;

    if (is >> customerName >> customerType >> customerDistance >> customerMaxOrders) {
        BaseAction *action = new AddCustomer(customerName, customerType, customerDistance, customerMaxOrders);
        action->act(*this);
        addAction(action);
    }
}

void WareHouse::processStep(std::istringstream &is) {
    int numOfSteps;
    if (is >> numOfSteps) {
        BaseAction *action = new SimulateStep(numOfSteps);
        action->act(*this);
        addAction(action);
    }
}

void WareHouse::processClose(std::istringstream&) {
    BaseAction *action = new Close();
    action->act(*this);
    addAction(action);
}

void WareHouse::processLog(std::istringstream&) {
    BaseAction *action = new PrintActionsLog();
    action->act(*this);
    addAction(action);
}

void WareHouse::processOrderStatus(std::istringstream &is) {
    int orderId;
    if (is >> orderId) {
        BaseAction *action = new PrintOrderStatus(orderId);
        action->act(*this);
        addAction(action);
    }
}

void WareHouse::processCustomerStatus(std::istringstream &is) {
    int customerId;
    if (is >> customerId) {
        BaseAction *action = new PrintCustomerStatus(customerId);
        action->act(*this);
        addAction(action);
    }
}

void WareHouse::processVolunteerStatus(std::istringstream &is) {
    int volunteerId;
    if (is >> volunteerId) {
        BaseAction *action = new PrintVolunteerStatus(volunteerId);
        action->act(*this);
        addAction(action);
    }
}

void WareHouse::processBackup(std::istringstream&) {
    BaseAction *action = new BackupWareHouse();
    action->act(*this);
    addAction(action);
}

void WareHouse::processRestore(std::istringstream&) {
    BaseAction *action = new RestoreWareHouse();
    action->act(*this);
    addAction(action);
}

void WareHouse::addOrder(Order* order) {
    if (order != nullptr) {
        pendingOrders.push_back(order);
        orderCounter++;
    }
}

void WareHouse::addCustomer(Customer* customer){
    if (customer != nullptr) {
        customers.push_back(customer);
        customerCounter++;
    }
}

void WareHouse::addAction(BaseAction* action) {
    if (action != nullptr) {
        actionsLog.push_back(action);
    }
}

void WareHouse::addOrderToInProcessOrders(Order *order) {
    if (order != nullptr) {
        inProcessOrders.push_back(order);
    }
}

void WareHouse::addOrderToCompletedOrders(Order *order) {
    if (order != nullptr) {
        completedOrders.push_back(order);
    }
}

void WareHouse::assignOrdersFromPendingToIsProcessOrders() {
    size_t index = 0;
    while (index < pendingOrders.size()) {
        Order *order = pendingOrders[index];
        Volunteer *assignedVolunteer = nullptr;

        for (Volunteer *volunteer : volunteers) {
            if (volunteer->canTakeOrder(*order)) {
                assignedVolunteer = volunteer;
                break;
            }
        }

        if (assignedVolunteer == nullptr) {
            index++;
            continue;
        }

        assignedVolunteer->acceptOrder(*order);
        if (assignedVolunteer->isCollector()) {
            order->setCollectorId(assignedVolunteer->getId());
            order->setStatus(OrderStatus::COLLECTING);
        } else {
            order->setDriverId(assignedVolunteer->getId());
            order->setStatus(OrderStatus::DELIVERING);
        }

        pendingOrders.erase(pendingOrders.begin() + index);
        inProcessOrders.push_back(order);
    }
}

void WareHouse::performStep() {
    for (Volunteer *volunteer : volunteers) {
        volunteer->step();
    }
}

void WareHouse::volunteersFinishedOrder() {
    size_t index = 0;
    while (index < inProcessOrders.size()) {
        Order *order = inProcessOrders[index];
        Volunteer *finishingVolunteer = nullptr;

        for (Volunteer *volunteer : volunteers) {
            if (!volunteer->isBusy() && volunteer->getCompletedOrderId() == order->getId()) {
                finishingVolunteer = volunteer;
                break;
            }
        }

        if (finishingVolunteer == nullptr) {
            index++;
            continue;
        }

        inProcessOrders.erase(inProcessOrders.begin() + index);
        if (order->getStatus() == OrderStatus::COLLECTING) {
            pendingOrders.push_back(order);
        } else if (order->getStatus() == OrderStatus::DELIVERING) {
            order->setStatus(OrderStatus::COMPLETED);
            completedOrders.push_back(order);
        }
        finishingVolunteer->resetCompletedOrderId();
    }
}

void WareHouse::deleteFinishedVolunteers() {
    size_t index = 0;
    while (index < volunteers.size()) {
        Volunteer *volunteer = volunteers[index];
        if (!volunteer->hasOrdersLeft() && !volunteer->isBusy()) {
            volunteers.erase(volunteers.begin() + index);
            delete volunteer;
        } else {
            index++;
        }
    }
}

void WareHouse::removeFromPendingOrders(Order *order) {
    detachPointer(pendingOrders, order);
}

void WareHouse::removeFromInProcessOrders(Order *order) {
    detachPointer(inProcessOrders, order);
}

void WareHouse::removeFromCompletedOrders(Order *order) {
    detachPointer(completedOrders, order);
}

void WareHouse::removeFromVolunteers(Volunteer* volunteer) {
    detachPointer(volunteers, volunteer);
}

void WareHouse::removeFromCustomers(Customer* customer) {
    detachPointer(customers, customer);
}

bool WareHouse::isDummyVolunteer(const Volunteer& volunteer) const {
    return volunteer.getId() == -1;
}

bool WareHouse::isDummyCustomer(const Customer& customer) const {
    return customer.getId() == -1;
}

bool WareHouse::isDummyOrder(const Order& order) const {
    return order.getId() == -1;
}

Customer &WareHouse::getCustomer(int customerId) const {
    for (Customer *customer : customers) {
        if (customer->getId() == customerId) {
            return *customer;
        }
    }

    throw std::out_of_range("Customer not found");
}

Volunteer &WareHouse::getVolunteer(int volunteerId) const {
    for (Volunteer *volunteer : volunteers) {
        if (volunteer->getId() == volunteerId) {
            return *volunteer;
        }
    }

    throw std::out_of_range("Volunteer not found");
}

Order &WareHouse::getOrder(int orderId) const {
    for (Order *order : pendingOrders) {
        if (order->getId() == orderId) {
            return *order;
        }
    }
    for (Order *order : inProcessOrders) {
        if (order->getId() == orderId) {
            return *order;
        }
    }
    for (Order *order : completedOrders) {
        if (order->getId() == orderId) {
            return *order;
        }
    }

    throw std::out_of_range("Order not found");
}

const vector<BaseAction*>& WareHouse::getActions() const {
    return actionsLog;
}

const vector<Volunteer*>& WareHouse::getVolunteers() const {
    return volunteers;
}

const vector<Order*>& WareHouse::getPendingOrders() const {
    return pendingOrders;
}

const vector<Order*>& WareHouse::getInProcessOrders() const {
    return inProcessOrders;
}

const vector<Order*>& WareHouse::getCompletedOrders() const {
    return completedOrders;
}

const vector<Customer*>& WareHouse::getCustomers() const {
    return customers;
}

int WareHouse::getSimulateTimeOfWorking() const{
    return simulateTimeOfWorking;
}

void WareHouse::increaseSimulateTimeOfWorking() {
    simulateTimeOfWorking++;
}

int WareHouse::getNextOrderId() const {
    return orderCounter;
}

int WareHouse::getNextCustomerId() const {
    return customerCounter;
}

void WareHouse::close() {
    isOpen = false;
}

void WareHouse::open() {
    isOpen = true;
}

void WareHouse::printAllOrdersStatus() {
    for (int orderId = 0; orderId < orderCounter; orderId++) {
        try {
            const Order &order = getOrder(orderId);
            std::cout << "OrderID: " << order.getId()
                      << " , CustomerID: " << order.getCustomerId()
                      << " , Status: " << orderStatusToString(order.getStatus()) << "\n";
        } catch (const std::out_of_range&) {
        }
    }
}
