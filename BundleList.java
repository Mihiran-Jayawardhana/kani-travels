package lk.bci.sdpgetbundlelist.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.bci.sdpgetbundlelist.util.SQLConnection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

@WebServlet(name = "bundleList", urlPatterns = "/bundleList")
public class BundleList extends HttpServlet {
    private SQLConnection sqlConnection = new SQLConnection();
    private String mobileNumber;
    private String token;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        //getting customer mobile number from the request
        mobileNumber = request.getParameter("mobileNumber");


        //checking mobile is a valid mobile number
        if (mobileNumber.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Mobile number is empty");
        } else if (mobileNumber.length() != 9) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Mobile number is length must be 9 characters");
        } else {
            //append 94 front for the mobile number
            mobileNumber = 94 + mobileNumber;
            //search token from database
            List<String[]> result = sqlConnection.executeSearch("SELECT accesstoken FROM SDPIntAccessToken WHERE status=? limit 1", "1");
            if (result.isEmpty()) {
                System.out.println("no result found");
            } else {
                for (String[] row : result) {
                    //assign token from database
                    token = row[0];


                }
            }


            //set up url and token
            String apiURL = "http://192.168.114.34:80/api/subscriptions/subscribed-service-bundles";
            //create HTTP POST request for the api url
            URL url = new URL(apiURL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setDoOutput(true);

            //assign  number as a json input
            String jsonInputString = "{\"number\":\"" + mobileNumber + "\"}";

            // write number as json input
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }


            //read response code
            int responseCode = connection.getResponseCode();
            System.out.println("Response Code : " + responseCode);

            //checking if the response code is 200
            if (responseCode == HttpURLConnection.HTTP_OK) {
                //read response
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder responseBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBuilder.append(line);
                }

                reader.close();
                connection.disconnect();

                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(responseBuilder.toString());

                //getting json data array
                JsonNode data = jsonNode.get("data");

                //data array  is empty or data array is not a array
                if (data == null || !data.isArray()) {
                    throw new RuntimeException("Invalid API response: 'data' field is missing or not an array");
                }

                //checking each json object item is_active ==1
                JsonNode filteredData = objectMapper.createArrayNode();
                for (JsonNode item : data) {
                    if (item.get("is_active") != null && item.get("is_active").asInt() == 1) {
                        ((com.fasterxml.jackson.databind.node.ArrayNode) filteredData).add(item);
                    }
                }

                //  Send Filtered Response Back to Client
                response.setContentType("application/json");
                response.getWriter().write(filteredData.toString());
                response.setStatus(HttpServletResponse.SC_OK);


            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("Failed to fetch bundle list");

            }

        }
    }
}
