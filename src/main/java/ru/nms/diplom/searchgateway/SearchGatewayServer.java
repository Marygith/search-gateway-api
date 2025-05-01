package ru.nms.diplom.searchgateway;

import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.protobuf.services.ProtoReflectionService;
import ru.nms.diplom.searchgateway.client.ClusterStateClient;
import ru.nms.diplom.searchgateway.service.SearchGatewayServiceImpl;

public class SearchGatewayServer {
    public static void main(String[] args) throws Exception {
        int port = 8080;

        var clusterStateClient = new ClusterStateClient();
        Server server = Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
                .addService(new SearchGatewayServiceImpl(clusterStateClient)) // assuming cluster state API is on 50051
                .addService(ProtoReflectionService.newInstance())
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
