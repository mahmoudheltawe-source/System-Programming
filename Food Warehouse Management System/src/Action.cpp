#include "Action.h"
#include "WareHouse.h"
#include "Volunteer.h"
#include "Customer.h"
#include "Order.h"

#include <algorithm>
#include <cctype>
#include <iostream>
#include <stdexcept>

namespace {
    string toLowerCopy(string value) {
        std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
            return static_cast<char>(std::tolower(c));
        });
        return value;
    }

    CustomerType parseCustomerType(const string &customerType) {
        return toLowerCopy(customerType) == "soldier" ? CustomerType::Soldier : CustomerType::Civilian;
    }

    string customerTypeToString(CustomerType customerType) {
        return customerType == CustomerType::Soldier ? "soldier" : "civilian";
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
}

BaseAction::BaseAction():
    errorMsg(""),
    status(ActionStatus::ERROR) {}

BaseAction::BaseAction(const BaseAction& other):
    errorMsg(other.errorMsg),
    status(other.status) {}

void BaseAction::complete() {
    status = ActionStatus::COMPLETED;
}

void BaseAction::error(string errorMsg) {
    status = ActionStatus::ERROR;
    this->errorMsg = errorMsg;
    std::cout << "Error: " << errorMsg << std::endl;
}

string BaseAction::getErrorMsg() const {
    return errorMsg;
}

ActionStatus BaseAction::getStatus() const {
    return status;
}

string BaseAction::getStatusString(ActionStatus status) const {
    return status == ActionStatus::COMPLETED ? "COMPLETED" : "ERROR";
}

SimulateStep::SimulateStep(int numOfSteps):
    BaseAction(),
    numOfSteps(numOfSteps) {}

SimulateStep::SimulateStep(const SimulateStep &other):
    BaseAction(other),
    numOfSteps(other.numOfSteps) {}

void SimulateStep::act(WareHouse &wareHouse) {
    for (int i = 0; i < numOfSteps; i++) {
        wareHouse.assignOrdersFromPendingToIsProcessOrders();
        wareHouse.performStep();
        wareHouse.volunteersFinishedOrder();
        wareHouse.deleteFinishedVolunteers();
        wareHouse.increaseSimulateTimeOfWorking();
    }
    complete();
}

SimulateStep *SimulateStep::clone() const {
    return new SimulateStep(*this);
}

string SimulateStep::toString() const {
    return "step " + std::to_string(numOfSteps);
}

AddOrder::AddOrder(int id):
    BaseAction(),
    customerId(id) {}

AddOrder::AddOrder(const AddOrder& other):
    BaseAction(other),
    customerId(other.customerId) {}

void AddOrder::act(WareHouse &wareHouse) {
    try {
        Customer &customer = wareHouse.getCustomer(customerId);
        if (!customer.canMakeOrder()) {
            error("Cannot place this order");
            return;
        }

        int orderId = wareHouse.getNextOrderId();
        Order *order = new Order(orderId, customerId, customer.getCustomerDistance());
        customer.addOrder(orderId);
        wareHouse.addOrder(order);
        complete();
    } catch (const std::out_of_range&) {
        error("Cannot place this order");
    }
}

AddOrder *AddOrder::clone() const {
    return new AddOrder(*this);
}

string AddOrder::toString() const {
    return "order " + std::to_string(customerId);
}

AddCustomer::AddCustomer(const string &customerName, const string &customerType, int distance, int maxOrders):
    BaseAction(),
    customerName(customerName),
    customerType(parseCustomerType(customerType)),
    distance(distance),
    maxOrders(maxOrders) {}

AddCustomer::AddCustomer(const AddCustomer &other):
    BaseAction(other),
    customerName(other.customerName),
    customerType(other.customerType),
    distance(other.distance),
    maxOrders(other.maxOrders) {}

void AddCustomer::act(WareHouse &wareHouse) {
    Customer *customer = nullptr;
    if (customerType == CustomerType::Soldier) {
        customer = new SoldierCustomer(wareHouse.getNextCustomerId(), customerName, distance, maxOrders);
    } else {
        customer = new CivilianCustomer(wareHouse.getNextCustomerId(), customerName, distance, maxOrders);
    }

    wareHouse.addCustomer(customer);
    complete();
}

AddCustomer *AddCustomer::clone() const {
    return new AddCustomer(*this);
}

string AddCustomer::toString() const {
    return "customer " + customerName + " " + customerTypeToString(customerType) + " " +
           std::to_string(distance) + " " + std::to_string(maxOrders);
}

PrintOrderStatus::PrintOrderStatus(int id):
    BaseAction(),
    orderId(id) {}

PrintOrderStatus::PrintOrderStatus(const PrintOrderStatus &other):
    BaseAction(other),
    orderId(other.orderId) {}

void PrintOrderStatus::act(WareHouse &wareHouse) {
    try {
        const Order &order = wareHouse.getOrder(orderId);
        std::cout << "OrderId: " << order.getId() << "\n";
        std::cout << "OrderStatus: " << orderStatusToString(order.getStatus()) << "\n";
        std::cout << "CustomerID: " << order.getCustomerId() << "\n";
        std::cout << "Collector: ";
        if (order.getCollectorId() == NO_VOLUNTEER) {
            std::cout << "None\n";
        } else {
            std::cout << order.getCollectorId() << "\n";
        }
        std::cout << "Driver: ";
        if (order.getDriverId() == NO_VOLUNTEER) {
            std::cout << "None\n";
        } else {
            std::cout << order.getDriverId() << "\n";
        }
        complete();
    } catch (const std::out_of_range&) {
        error("Order doesn't exist");
    }
}

PrintOrderStatus *PrintOrderStatus::clone() const {
    return new PrintOrderStatus(*this);
}

string PrintOrderStatus::toString() const {
    return "orderStatus " + std::to_string(orderId);
}

PrintCustomerStatus::PrintCustomerStatus(int customerId):
    BaseAction(),
    customerId(customerId) {}

PrintCustomerStatus::PrintCustomerStatus(const PrintCustomerStatus &other):
    BaseAction(other),
    customerId(other.customerId) {}

void PrintCustomerStatus::act(WareHouse &wareHouse) {
    try {
        const Customer &customer = wareHouse.getCustomer(customerId);
        std::cout << "CustomerID: " << customer.getId() << "\n";
        for (int orderId : customer.getOrdersIds()) {
            const Order &order = wareHouse.getOrder(orderId);
            std::cout << "OrderId: " << orderId << "\n";
            std::cout << "OrderStatus: " << orderStatusToString(order.getStatus()) << "\n";
        }
        std::cout << "numOrdersLeft: " << customer.getMaxOrders() - customer.getNumOrders() << "\n";
        complete();
    } catch (const std::out_of_range&) {
        error("Customer doesn't exist");
    }
}

PrintCustomerStatus *PrintCustomerStatus::clone() const {
    return new PrintCustomerStatus(*this);
}

string PrintCustomerStatus::toString() const {
    return "customerStatus " + std::to_string(customerId);
}

PrintVolunteerStatus::PrintVolunteerStatus(int id):
    BaseAction(),
    volunteerId(id) {}

PrintVolunteerStatus::PrintVolunteerStatus(const PrintVolunteerStatus &other):
    BaseAction(other),
    volunteerId(other.volunteerId) {}

void PrintVolunteerStatus::act(WareHouse &wareHouse) {
    try {
        Volunteer &volunteer = wareHouse.getVolunteer(volunteerId);
        std::cout << "VolunteerID: " << volunteer.getId() << "\n";
        std::cout << "isBusy: " << (volunteer.isBusy() ? "True" : "False") << "\n";

        if (volunteer.isBusy()) {
            std::cout << "OrderId: " << volunteer.getActiveOrderId() << "\n";
            std::cout << "TimeLeft: ";
            if (volunteer.isCollector()) {
                std::cout << static_cast<CollectorVolunteer&>(volunteer).getTimeLeft() << "\n";
            } else {
                std::cout << static_cast<DriverVolunteer&>(volunteer).getDistanceLeft() << "\n";
            }
        } else {
            std::cout << "OrderId: None\n";
            std::cout << "TimeLeft: None\n";
        }

        std::cout << "OrdersLeft: ";
        if (!volunteer.isLimited()) {
            std::cout << "No Limit\n";
        } else if (volunteer.isCollector()) {
            std::cout << static_cast<LimitedCollectorVolunteer&>(volunteer).getNumOrdersLeft() << "\n";
        } else {
            std::cout << static_cast<LimitedDriverVolunteer&>(volunteer).getNumOrdersLeft() << "\n";
        }

        complete();
    } catch (const std::out_of_range&) {
        error("Volunteer doesn't exist");
    }
}

PrintVolunteerStatus *PrintVolunteerStatus::clone() const {
    return new PrintVolunteerStatus(*this);
}

string PrintVolunteerStatus::toString() const {
    return "volunteerStatus " + std::to_string(volunteerId);
}

PrintActionsLog::PrintActionsLog():
    BaseAction() {}

PrintActionsLog::PrintActionsLog(const PrintActionsLog &other):
    BaseAction(other) {}

void PrintActionsLog::act(WareHouse &wareHouse) {
    const vector<BaseAction*> &actionsLog = wareHouse.getActions();
    for (const BaseAction *action : actionsLog) {
        std::cout << action->toString() << " " << action->getStatusString(action->getStatus()) << "\n";
    }
    complete();
}

PrintActionsLog *PrintActionsLog::clone() const {
    return new PrintActionsLog(*this);
}

string PrintActionsLog::toString() const {
    return "log";
}

Close::Close():
    BaseAction() {}

Close::Close(const Close &other):
    BaseAction(other) {}

void Close::act(WareHouse &wareHouse) {
    wareHouse.printAllOrdersStatus();
    wareHouse.close();
    complete();
}

Close *Close::clone() const {
    return new Close(*this);
}

string Close::toString() const {
    return "close";
}

BackupWareHouse::BackupWareHouse():
    BaseAction() {}

BackupWareHouse::BackupWareHouse(const BackupWareHouse &other):
    BaseAction(other) {}

void BackupWareHouse::act(WareHouse &wareHouse) {
    delete backup;
    backup = new WareHouse(wareHouse);
    complete();
}

BackupWareHouse *BackupWareHouse::clone() const {
    return new BackupWareHouse(*this);
}

string BackupWareHouse::toString() const {
    return "backup";
}

RestoreWareHouse::RestoreWareHouse():
    BaseAction() {}

RestoreWareHouse::RestoreWareHouse(const RestoreWareHouse &other):
    BaseAction(other) {}

void RestoreWareHouse::act(WareHouse &wareHouse) {
    if (backup == nullptr) {
        error("No backup available");
        return;
    }

    wareHouse = *backup;
    complete();
}

RestoreWareHouse *RestoreWareHouse::clone() const {
    return new RestoreWareHouse(*this);
}

string RestoreWareHouse::toString() const {
    return "restore";
}
