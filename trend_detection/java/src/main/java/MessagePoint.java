import org.apache.commons.math3.ml.clustering.Clusterable;


public class MessagePoint implements Clusterable {
    private final InputMessage message;
    private final double[] embedding;

    public MessagePoint(InputMessage message) {
        this.message = message;
        this.embedding = message.getEmbedding();
    }

    public InputMessage getMessage() {
        return message;
    }

    @Override
    public double[] getPoint() {
        return message.getEmbedding();
    }
}