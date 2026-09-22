package com.example.baget.util;

import com.google.auth.oauth2.GoogleCredentials;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CloudRunScaleService {

    @Value("${gcp.region}")
    private String region;

    @Value("${gcp.service-name}")
    private String serviceName;

    @Value("${gcp.project-id}")
    private String projectId;


    public void updateMinInstances(int count) throws Exception {

        GoogleCredentials credentials =
                GoogleCredentials.getApplicationDefault()
                        .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));

        credentials.refreshIfExpired();

        String token = credentials.getAccessToken().getTokenValue();


        String url = "https://run.googleapis.com/v2/projects/" + projectId +
                "/locations/" + region +
                "/services/" + serviceName +
                "?updateMask=template.scaling.minInstanceCount";

        String body = """
        {
          "template": {
            "scaling": {
              "minInstanceCount": %d
            }
          }
        }
        """.formatted(count);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .build();

        var response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Status: " + response.statusCode());
        System.out.println("Body: " + response.body());

    }
}
