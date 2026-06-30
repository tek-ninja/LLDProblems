package FoodDeliveryService.models;


public class Order {
    private final String id;
    private final Customer customer;
    private final Restaurant restaurant;
    private final List<OrderItem> items;
    private OrderStatus status;
    private DeliveryAgent deliveryAgent;
    private final List<OrderObserver> observers = new ArrayList<>();

    public Order(Customer customer, Restaurant restaurant, List<OrderItem> items) {
        this.id = UUID.randomUUID().toString();
        this.customer = customer;
        this.restaurant = restaurant;
        this.items = items;
        this.status = OrderStatus.PENDING;
        addObserver(customer);
        addObserver(restaurant);
        notifyObservers();
    }

    public void addObserver(OrderObserver observer) { observers.add(observer); }
    private void notifyObservers() { observers.forEach(o -> o.onUpdate(this)); }

    public void setStatus(OrderStatus newStatus) {
        if (this.status != newStatus) {
            this.status = newStatus;
            if (newStatus == OrderStatus.DELIVERED && deliveryAgent != null) {
                deliveryAgent.setAvailable(true);
            }
            notifyObservers();
        }
    }

    public boolean cancel() {
        // Only allow cancellation if the order is still in the PENDING state.
        if (this.status == OrderStatus.PENDING || this.status == OrderStatus.CONFIRMED) {
            setStatus(OrderStatus.CANCELLED);
            return true;
        }
        return false;
    }

    public void assignDeliveryAgent(DeliveryAgent agent) {
        this.deliveryAgent = agent;
        addObserver(agent);
        agent.setAvailable(false); // Mark agent as busy
    }

    // Getters
    public String getId() { return id; }
    public OrderStatus getStatus() { return status; }
    public Customer getCustomer() { return customer; }
    public Restaurant getRestaurant() { return restaurant; }
    public DeliveryAgent getDeliveryAgent() { return deliveryAgent; }

    public double getTotalAmount() {
        return items.stream().mapToDouble(OrderItem::getSubtotal).sum();
    }
}