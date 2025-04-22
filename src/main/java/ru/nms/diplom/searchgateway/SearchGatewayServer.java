package ru.nms.diplom.searchgateway;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import ru.nms.diplom.searchgateway.client.ClusterStateClient;
import ru.nms.diplom.searchgateway.service.SearchGatewayServiceImpl;

public class SearchGatewayServer {
    public static void main(String[] args) throws Exception {
        int port = 8080;

        var clusterStateClient = new ClusterStateClient("localhost", 50052);
        Server server = ServerBuilder.forPort(port)
                .addService(new SearchGatewayServiceImpl(clusterStateClient)) // assuming cluster state API is on 50051
                .build();

        System.out.println("🚀 Gateway gRPC server started on port " + port);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("🛑 Shutting down Gateway gRPC server...");
            server.shutdown();
        }));

        server.awaitTermination();
    }
}
