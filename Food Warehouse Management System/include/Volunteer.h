#pragma once

#include <string>
#include "Order.h"

using std::string;

#define NO_ORDER -1

class Volunteer {
    public:
        Volunteer(int id, const string &name);
        Volunteer();
        Volunteer(const Volunteer &other);
        virtual ~Volunteer() = default;

        int getId() const;
        const string &getName() const;
        int getActiveOrderId() const;
        int getCompletedOrderId() const;
        void resetCompletedOrderId();
        bool isBusy() const;

        virtual bool hasOrdersLeft() const = 0;
        virtual bool canTakeOrder(const Order &order) const = 0;
        virtual void acceptOrder(const Order &order) = 0;
        virtual bool isDriver() const = 0;
        virtual bool isCollector() const = 0;
        virtual bool isLimited() const = 0;
        virtual void step() = 0;
        virtual string toString() const = 0;
        virtual Volunteer* clone() const = 0;

    private:
        const int id;
        const string name;

    protected:
        int completedOrderId;
        int activeOrderId;
};

class CollectorVolunteer: public Volunteer {
    public:
        CollectorVolunteer(int id, const string &name, int coolDown);
        CollectorVolunteer();
        CollectorVolunteer(const CollectorVolunteer &other);
        CollectorVolunteer *clone() const override;

        bool isCollector() const override;
        bool isDriver() const override;
        bool isLimited() const override;
        void step() override;
        int getCoolDown() const;
        int getTimeLeft() const;
        bool decreaseCoolDown();
        bool hasOrdersLeft() const override;
        bool canTakeOrder(const Order &order) const override;
        void acceptOrder(const Order &order) override;
        string toString() const override;

    private:
        const int coolDown;
        int timeLeft;
};

class LimitedCollectorVolunteer: public CollectorVolunteer {
    public:
        LimitedCollectorVolunteer(int id, const string &name, int coolDown ,int maxOrders);
        LimitedCollectorVolunteer();
        LimitedCollectorVolunteer(const LimitedCollectorVolunteer &other);
        LimitedCollectorVolunteer *clone() const override;

        bool hasOrdersLeft() const override;
        bool canTakeOrder(const Order &order) const override;
        void acceptOrder(const Order &order) override;
        bool isLimited() const override;
        void decreaseOrdersLeft();
        int getMaxOrders() const;
        int getNumOrdersLeft() const;
        string toString() const override;

    private:
        const int maxOrders;
        int ordersLeft;
};

class DriverVolunteer: public Volunteer {
    public:
        DriverVolunteer(int id, const string &name, int maxDistance, int distancePerStep);
        DriverVolunteer();
        DriverVolunteer(const DriverVolunteer &other);
        DriverVolunteer *clone() const override;

        bool isCollector() const override;
        bool isDriver() const override;
        bool isLimited() const override;
        int getDistanceLeft() const;
        int getMaxDistance() const;
        int getDistancePerStep() const;
        bool decreaseDistanceLeft();
        bool hasOrdersLeft() const override;
        bool canTakeOrder(const Order &order) const override;
        void acceptOrder(const Order &order) override;
        void step() override;
        string toString() const override;

    private:
        const int maxDistance;
        const int distancePerStep;
        int distanceLeft;
};

class LimitedDriverVolunteer: public DriverVolunteer {
    public:
        LimitedDriverVolunteer(int id, const string &name, int maxDistance, int distancePerStep,int maxOrders);
        LimitedDriverVolunteer();
        LimitedDriverVolunteer(const LimitedDriverVolunteer &other);
        LimitedDriverVolunteer *clone() const override;

        int getMaxOrders() const;
        int getNumOrdersLeft() const;
        void decreaseOrdersLeft();
        bool isLimited() const override;
        bool hasOrdersLeft() const override;
        bool canTakeOrder(const Order &order) const override;
        void acceptOrder(const Order &order) override;
        string toString() const override;

    private:
        const int maxOrders;
        int ordersLeft;
};
