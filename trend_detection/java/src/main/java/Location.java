public class Location {
    private int id;
    private double lat;
    private double lon;
    private String name;
    private String state;
    private String region;

    public Location(int id, double lat, double lon, String name, String state, String region) {
        this.id = id;
        this.lat = lat;
        this.lon = lon;
        this.name = name;
        this.state = state;
        this.region = region;
    }

    public int getId() { return id; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public String getName() { return name; }
}