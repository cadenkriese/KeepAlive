package codes.caden.keepalive.config;

public class Config {
    public ServerAddress limboServer;
    public ServerAddress destinationServer;

    public static final Config DEFAULT = new Config(new ServerAddress("", 0), new ServerAddress("", 0));

    public Config(ServerAddress limboServer, ServerAddress destinationServer) {
        this.limboServer = limboServer;
        this.destinationServer = destinationServer;
    }
}

