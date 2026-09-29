public class Product {
    private String productId;
    private String name;
    private String description;
    private String condition;

    public Product(String productId, String name, String description, String condition) {
        this.productId = productId;
        this.name = name;
        this.description = description;
        this.condition = condition;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    @Override
    public String toString() {
        return name + " [" + productId + "]";
    }
}
