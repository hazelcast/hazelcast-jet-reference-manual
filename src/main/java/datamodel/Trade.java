package datamodel;

public class Trade extends Event {
    private int productId;
    private int brokerId;
    private int marketId;

    Trade(int userId, long timestamp) {
        super(userId, timestamp);
    }

    public int productId() {
        return productId;
    }

    public int brokerId() {
        return brokerId;
    }

    public int marketId() {
        return marketId;
    }
}
