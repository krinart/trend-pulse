public class ClusterResult {
    private float[] centroid;

    public ClusterResult(float[] centroid) {
        this.centroid = centroid;
    }

    public float[] getCentroid() { return centroid; }
}