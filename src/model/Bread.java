package model;

public class Bread extends Product {
    private double weightInGrams;

    public Bread(String id, String name, double price, int stock, String description, double weightInGrams) {
        super(id, name, "Bread", price, stock, description);
        this.weightInGrams = weightInGrams;
    }

    public double getWeightInGrams() { return weightInGrams; }
    public void setWeightInGrams(double weightInGrams) { this.weightInGrams = weightInGrams; }

    @Override
    public String displayDetails() {
        return "Bread: " + getName() + " (" + weightInGrams + "g) - Price: Rs." + getPrice();
    }

    @Override
    public double calculatePrice() {
        return getPrice();
    }
}