#include "Order.h"

namespace {
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

Order::Order(int id, int customerId, int distance):
    id(id),
    customerId(customerId),
    distance(distance),
    status(OrderStatus::PENDING),
    collectorId(NO_VOLUNTEER),
    driverId(NO_VOLUNTEER) {}

Order::Order():
    id(-1),
    customerId(-1),
    distance(0),
    status(OrderStatus::PENDING),
    collectorId(NO_VOLUNTEER),
    driverId(NO_VOLUNTEER) {}

Order::Order(const Order &other):
    id(other.id),
    customerId(other.customerId),
    distance(other.distance),
    status(other.status),
    collectorId(other.collectorId),
    driverId(other.driverId) {}

int Order::getId() const {
    return id;
}

int Order::getCustomerId() const{
    return customerId;
}

int Order::getDistance() const {
    return distance;
}

void Order::setStatus(OrderStatus status) {
    this->status = status;
}

void Order::setCollectorId(int collectorId) {
    this->collectorId = collectorId;
}

void Order::setDriverId(int driverId) {
    this->driverId = driverId;
}

int Order::getCollectorId() const {
    return collectorId;
}

int Order::getDriverId() const {
    return driverId;
}

OrderStatus Order::getStatus() const {
    return status;
}

const string Order::toString() const {
    return "OrderID: " + std::to_string(id) +
           " , CustomerID: " + std::to_string(customerId) +
           " , Status: " + orderStatusToString(status);
}

Order *Order::clone() const {
    return new Order(*this);
}
