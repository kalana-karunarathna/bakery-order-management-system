package model;

public class Pastry extends Product {
    private boolean isSavory;

    public Pastry(String id, String name, double price, int stock, String description, boolean isSavory) {
        super(id, name, "Pastry", price, stock, description);
        this.isSavory = isSavory;
    }

    public boolean isSavory() { return isSavory; }
    public void setSavory(boolean savory) { isSavory = savory; }

    @Override
    public String displayDetails() {
        String type = isSavory ? "Savory" : "Sweet";
        return "Pastry: " + getName() + " (" + type + ") - Price: Rs." + getPrice();
    }

    @Override
    public double calculatePrice() {
        return getPrice();
    }
}