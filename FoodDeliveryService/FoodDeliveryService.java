package FoodDeliveryService.strategies;

import FoodDeliveryService.models.*;
import java.util.concurrent.ConcurrentHashMap;

class FoodDeliveryService {
    private static volatile FoodDeliveryService instance;
    private final Map<String, Customer> customers = new ConcurrentHashMap<>();
    private final Map<String, Restaurant> restaurants = new ConcurrentHashMap<>();
    private final Map<String, DeliveryAgent> deliveryAgents = new ConcurrentHashMap<>();
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private DeliveryAssignmentStrategy assignmentStrategy = new NearestAvailableAgentStrategy();

    private FoodDeliveryService() {}

    public static FoodDeliveryService getInstance() {
        if (instance == null) {
            synchronized (FoodDeliveryService.class) {
                if (instance == null) instance = new FoodDeliveryService();
            }
        }
        return instance;
    }

    public void setAssignmentStrategy(DeliveryAssignmentStrategy assignmentStrategy) {
        this.assignmentStrategy = assignmentStrategy;
    }

    // --- Registration ---
    public Customer registerCustomer(String name, String phone, Address address) {
        Customer customer = new Customer(name, phone, address);
        customers.put(customer.getId(), customer);
        return customer;
    }

    public Restaurant registerRestaurant(String name, Address address) {
        Restaurant restaurant = new Restaurant(name, address);
        restaurants.put(restaurant.getId(), restaurant);
        return restaurant;
    }

    public DeliveryAgent registerDeliveryAgent(String name, String phone, Address initialLocation) {
        DeliveryAgent deliveryAgent = new DeliveryAgent(name, phone, initialLocation);
        deliveryAgents.put(deliveryAgent.getId(), deliveryAgent);
        return deliveryAgent;
    }

    public Order placeOrder(String customerId, String restaurantId, List<OrderItem> items) {
        Customer customer = customers.get(customerId);
        Restaurant restaurant = restaurants.get(restaurantId);
        if (customer == null || restaurant == null) throw new NoSuchElementException("Customer or Restaurant not found.");
        if (items == null || items.isEmpty())
            throw new IllegalArgumentException("Order must contain at least one item.");
        for (OrderItem orderItem : items) {
            if (orderItem.getQuantity() <= 0)
                throw new IllegalArgumentException("Item quantity must be positive.");
            if (!orderItem.getItem().isAvailable())
                throw new IllegalStateException(orderItem.getItem().getName() + " is not available.");
        }

        Order order = new Order(customer, restaurant, items);
        orders.put(order.getId(), order);
        customer.addOrderToHistory(order);
        System.out.printf("Order %s placed by %s at %s.\n", order.getId(), customer.getName(), restaurant.getName());
        // Initial PENDING status is set in constructor and observers are notified.
        order.setStatus(OrderStatus.PENDING);
        return order;
    }

    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = Map.of(
            OrderStatus.PENDING, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, Set.of(OrderStatus.PREPARING, OrderStatus.CANCELLED),
            OrderStatus.PREPARING, Set.of(OrderStatus.READY_FOR_PICKUP),
            OrderStatus.READY_FOR_PICKUP, Set.of(OrderStatus.OUT_FOR_DELIVERY),
            OrderStatus.OUT_FOR_DELIVERY, Set.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, Set.of(),
            OrderStatus.CANCELLED, Set.of());

    public void updateOrderStatus(String orderId, OrderStatus newStatus) {
        Order order = orders.get(orderId);
        if (order == null)
            throw new NoSuchElementException("Order not found.");

        if (!VALID_TRANSITIONS.getOrDefault(order.getStatus(), Set.of()).contains(newStatus))
            throw new IllegalStateException("Invalid transition: " + order.getStatus() + " -> " + newStatus);

        order.setStatus(newStatus);

        // If order is ready, find a delivery agent.
        if (newStatus == OrderStatus.READY_FOR_PICKUP) {
            assignDelivery(order);
        }
    }

    public void cancelOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order == null) {
            System.out.println("ERROR: Order with ID " + orderId + " not found.");
            return;
        }

        // Delegate the cancellation logic to the Order object itself.
        if (order.cancel()) {
            System.out.println("SUCCESS: Order " + orderId + " has been successfully canceled.");
        } else {
            System.out.println("FAILED: Order " + orderId + " could not be canceled. Its status is: " + order.getStatus());
        }
    }

    private void assignDelivery(Order order) {
        List<DeliveryAgent> availableAgents = new ArrayList<>(deliveryAgents.values());

        assignmentStrategy.findAgent(order, availableAgents).ifPresentOrElse(
                agent -> {
                    order.assignDeliveryAgent(agent);
                    System.out.printf("Agent %s (dist: %.2f) assigned to order %s.\n",
                            agent.getName(),
                            agent.getCurrentLocation().distanceTo(order.getRestaurant().getAddress()),
                            order.getId());
                    order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
                },
                () -> System.out.println("No available delivery agents found for order " + order.getId())
        );
    }

    public List<Restaurant> searchRestaurants(List<RestaurantSearchStrategy> strategies) {
        List<Restaurant> results = new ArrayList<>(restaurants.values());

        for (RestaurantSearchStrategy strategy : strategies) {
            results = strategy.filter(results);
        }

        return results;
    }

    public Menu getRestaurantMenu(String restaurantId) {
        Restaurant restaurant = restaurants.get(restaurantId);
        if (restaurant == null) {
            throw new NoSuchElementException("Restaurant with ID " + restaurantId + " not found.");
        }
        return restaurant.getMenu();
    }
}