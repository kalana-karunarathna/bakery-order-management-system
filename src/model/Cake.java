package model;

public class Cake extends Product {
    private String flavor;

    public Cake(String id, String name, double price, int stock, String description, String flavor) {
        super(id, name, "Cake", price, stock, description);
        this.flavor = flavor;
    }

    public String getFlavor() { return flavor; }
    public void setFlavor(String flavor) { this.flavor = flavor; }

    @Override
    public String displayDetails() {
        return "Cake: " + getName() + " [" + flavor + "] - Price: Rs." + getPrice();
    }

    @Override
    public double calculatePrice() {
        return getPrice();
    }
}