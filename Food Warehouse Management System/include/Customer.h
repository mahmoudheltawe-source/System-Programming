#pragma once

#include <string>
#include <vector>

using std::string;
using std::vector;

class Customer {
    public:
        Customer(int id, const string &name, int locationDistance, int maxOrders);
        Customer();
        Customer(const Customer &other);
        virtual ~Customer() = default;

        const string &getName() const;
        int getId() const;
        int getCustomerDistance() const;
        int getMaxOrders() const;
        int getNumOrders() const;
        bool canMakeOrder() const;
        const vector<int> &getOrdersIds() const;
        int addOrder(int orderId);

        virtual Customer *clone() const = 0;

    private:
        const int id;
        const string name;
        const int locationDistance;
        const int maxOrders;
        vector<int> ordersId;
};

class SoldierCustomer: public Customer {
    public:
        SoldierCustomer(int id, const string &name, int locationDistance, int maxOrders);
        SoldierCustomer();
        SoldierCustomer(const SoldierCustomer &other);
        SoldierCustomer *clone() const override;
};

class CivilianCustomer: public Customer {
    public:
        CivilianCustomer(int id, const string &name, int locationDistance, int maxOrders);
        CivilianCustomer();
        CivilianCustomer(const CivilianCustomer &other);
        CivilianCustomer *clone() const override;
};
