package demo;

import validation.NotBlank;
import validation.Pattern;
import validation.Size;

public record NameRequest(
    @NotBlank @Size(max = 40) @Pattern(regexp = "[\\p{L} .'-]+") String name) {
}
