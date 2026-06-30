package FoodDeliveryService.observer;

import FoodDeliveryService.models.*;

public interface OrderObserver {
    void onUpdate(Order order);
}