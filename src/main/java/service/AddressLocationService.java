package service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;

@Service
public class AddressLocationService {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ward(int code, String name) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record District(int code, String name, List<Ward> wards) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Location(int code, String name, List<District> districts) { }
    private final List<Location> locations;

    public AddressLocationService() throws IOException {
        try (var stream = new ClassPathResource("static/data/vietnam-addresses-v1.json").getInputStream()) {
            locations = List.of(new ObjectMapper().readValue(stream, Location[].class));
        }
    }
    public List<Location> getLocations() { return locations; }
    public Location validate(String code, String district, String ward) {
        return locations.stream().filter(p -> String.valueOf(p.code()).equals(code)
                && p.districts().stream().anyMatch(d -> d.name().equals(district)
                && d.wards().stream().anyMatch(w -> w.name().equals(ward))))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Tỉnh, huyện và xã không hợp lệ. Vui lòng chọn lại."));
    }
}
