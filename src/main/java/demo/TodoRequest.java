package demo;

import validation.NotBlank;
import validation.Size;

public record TodoRequest(@NotBlank @Size(max = 100) String title, boolean done) {
}
