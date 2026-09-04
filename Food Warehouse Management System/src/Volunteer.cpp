#include "Volunteer.h"

Volunteer::Volunteer(int id, const string &name):
    id(id),
    name(name),
    completedOrderId(NO_ORDER),
    activeOrderId(NO_ORDER) {}

Volunteer::Volunteer():
    id(-1),
    name(""),
    completedOrderId(NO_ORDER),
    activeOrderId(NO_ORDER) {}

Volunteer::Volunteer(const Volunteer &other):
    id(other.id),
    name(other.name),
    completedOrderId(other.completedOrderId),
    activeOrderId(other.activeOrderId) {}

int Volunteer::getId() const {
    return id;
}

const string &Volunteer::getName() const {
    return name;
}

int Volunteer::getActiveOrderId() const {
    return activeOrderId;
}

int Volunteer::getCompletedOrderId() const {
    return completedOrderId;
}

void Volunteer::resetCompletedOrderId() {
    completedOrderId = NO_ORDER;
}

bool Volunteer::isBusy() const {
    return activeOrderId != NO_ORDER;
}

CollectorVolunteer::CollectorVolunteer(int id, const string &name, int coolDown):
    Volunteer(id, name),
    coolDown(coolDown),
    timeLeft(0) {}

CollectorVolunteer::CollectorVolunteer():
    Volunteer(),
    coolDown(0),
    timeLeft(0) {}

CollectorVolunteer::CollectorVolunteer(const CollectorVolunteer &other):
    Volunteer(other),
    coolDown(other.coolDown),
    timeLeft(other.timeLeft) {}

CollectorVolunteer *CollectorVolunteer::clone() const{
    return new CollectorVolunteer(*this);
}

bool CollectorVolunteer::isCollector() const {
    return true;
}

bool CollectorVolunteer::isDriver() const {
    return false;
}

bool CollectorVolunteer::isLimited() const {
    return false;
}

void CollectorVolunteer::step(){
    if (activeOrderId != NO_ORDER && decreaseCoolDown()) {
        completedOrderId = activeOrderId;
        activeOrderId = NO_ORDER;
    }
}

int CollectorVolunteer::getCoolDown() const {
    return coolDown;
}

int CollectorVolunteer::getTimeLeft() const {
    return timeLeft;
}

bool CollectorVolunteer::decreaseCoolDown() {
    if (timeLeft > 0) {
        timeLeft--;
    }

    return timeLeft == 0;
}

bool CollectorVolunteer::hasOrdersLeft() const{
    return true;
}

bool CollectorVolunteer::canTakeOrder(const Order &order) const {
    return !isBusy() && order.getStatus() == OrderStatus::PENDING;
}

void CollectorVolunteer::acceptOrder(const Order &order) {
    completedOrderId = NO_ORDER;
    activeOrderId = order.getId();
    timeLeft = coolDown;
}

string CollectorVolunteer::toString() const {
    return "collector " + getName() + " " + std::to_string(getCoolDown());
}

LimitedCollectorVolunteer::LimitedCollectorVolunteer(int id, const string &name, int coolDown, int maxOrders):
    CollectorVolunteer(id, name, coolDown),
    maxOrders(maxOrders),
    ordersLeft(maxOrders) {}

LimitedCollectorVolunteer::LimitedCollectorVolunteer():
    CollectorVolunteer(),
    maxOrders(0),
    ordersLeft(0) {}

LimitedCollectorVolunteer::LimitedCollectorVolunteer(const LimitedCollectorVolunteer &other):
    CollectorVolunteer(other),
    maxOrders(other.maxOrders),
    ordersLeft(other.ordersLeft) {}

LimitedCollectorVolunteer *LimitedCollectorVolunteer::clone() const{
    return new LimitedCollectorVolunteer(*this);
}

bool LimitedCollectorVolunteer::hasOrdersLeft() const{
    return ordersLeft > 0;
}

bool LimitedCollectorVolunteer::canTakeOrder(const Order &order) const{
    return hasOrdersLeft() && CollectorVolunteer::canTakeOrder(order);
}

void LimitedCollectorVolunteer::acceptOrder(const Order &order){
    CollectorVolunteer::acceptOrder(order);
    decreaseOrdersLeft();
}

bool LimitedCollectorVolunteer::isLimited() const {
    return true;
}

void LimitedCollectorVolunteer::decreaseOrdersLeft() {
    if (ordersLeft > 0) {
        ordersLeft--;
    }
}

int LimitedCollectorVolunteer::getMaxOrders() const{
    return maxOrders;
}

int LimitedCollectorVolunteer::getNumOrdersLeft() const{
    return ordersLeft;
}

string LimitedCollectorVolunteer::toString() const{
    return "limited_collector " + getName() + " " + std::to_string(getCoolDown()) + " " + std::to_string(maxOrders);
}

DriverVolunteer::DriverVolunteer(int id, const string &name, int maxDistance, int distancePerStep):
    Volunteer(id, name),
    maxDistance(maxDistance),
    distancePerStep(distancePerStep),
    distanceLeft(0) {}

DriverVolunteer::DriverVolunteer():
    Volunteer(),
    maxDistance(0),
    distancePerStep(0),
    distanceLeft(0) {}

DriverVolunteer::DriverVolunteer(const DriverVolunteer &other):
    Volunteer(other),
    maxDistance(other.maxDistance),
    distancePerStep(other.distancePerStep),
    distanceLeft(other.distanceLeft) {}

DriverVolunteer *DriverVolunteer::clone() const {
    return new DriverVolunteer(*this);
}

bool DriverVolunteer::isCollector() const {
    return false;
}

bool DriverVolunteer::isDriver() const {
    return true;
}

bool DriverVolunteer::isLimited() const {
    return false;
}

int DriverVolunteer::getDistanceLeft() const {
    return distanceLeft;
}

int DriverVolunteer::getMaxDistance() const {
    return maxDistance;
}

int DriverVolunteer::getDistancePerStep() const {
    return distancePerStep;
}

bool DriverVolunteer::decreaseDistanceLeft() {
    if (distanceLeft > 0) {
        distanceLeft -= distancePerStep;
    }

    if (distanceLeft <= 0) {
        distanceLeft = 0;
        return true;
    }

    return false;
}

bool DriverVolunteer::hasOrdersLeft() const {
    return true;
}

bool DriverVolunteer::canTakeOrder(const Order &order) const {
    return !isBusy() &&
           order.getStatus() == OrderStatus::COLLECTING &&
           order.getDistance() <= maxDistance;
}

void DriverVolunteer::acceptOrder(const Order &order){
    completedOrderId = NO_ORDER;
    activeOrderId = order.getId();
    distanceLeft = order.getDistance();
}

void DriverVolunteer::step() {
    if (activeOrderId != NO_ORDER && decreaseDistanceLeft()) {
        completedOrderId = activeOrderId;
        activeOrderId = NO_ORDER;
    }
}

string DriverVolunteer::toString() const{
    return "driver " + getName() + " " + std::to_string(maxDistance) + " " + std::to_string(distancePerStep);
}

LimitedDriverVolunteer::LimitedDriverVolunteer(int id, const string &name, int maxDistance, int distancePerStep, int maxOrders):
    DriverVolunteer(id, name, maxDistance, distancePerStep),
    maxOrders(maxOrders),
    ordersLeft(maxOrders) {}

LimitedDriverVolunteer::LimitedDriverVolunteer():
    DriverVolunteer(),
    maxOrders(0),
    ordersLeft(0) {}

LimitedDriverVolunteer::LimitedDriverVolunteer(const LimitedDriverVolunteer &other):
    DriverVolunteer(other),
    maxOrders(other.maxOrders),
    ordersLeft(other.ordersLeft) {}

LimitedDriverVolunteer *LimitedDriverVolunteer::clone() const {
    return new LimitedDriverVolunteer(*this);
}

int LimitedDriverVolunteer::getMaxOrders() const {
    return maxOrders;
}

int LimitedDriverVolunteer::getNumOrdersLeft() const {
    return ordersLeft;
}

void LimitedDriverVolunteer::decreaseOrdersLeft() {
    if (ordersLeft > 0) {
        ordersLeft--;
    }
}

bool LimitedDriverVolunteer::isLimited() const {
    return true;
}

bool LimitedDriverVolunteer::hasOrdersLeft() const {
    return ordersLeft > 0;
}

bool LimitedDriverVolunteer::canTakeOrder(const Order &order) const {
    return hasOrdersLeft() && DriverVolunteer::canTakeOrder(order);
}

void LimitedDriverVolunteer::acceptOrder(const Order &order) {
    DriverVolunteer::acceptOrder(order);
    decreaseOrdersLeft();
}

string LimitedDriverVolunteer::toString() const {
    return "limited_driver " + getName() + " " + std::to_string(getMaxDistance()) + " " +
           std::to_string(getDistancePerStep()) + " " + std::to_string(maxOrders);
}
