package io.github.peterberghuis.profile.validator;

import io.github.peterberghuis.profile.dto.UserPreferences;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class UserPreferencesValidator {

    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^$|^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$");
    private static final Pattern URL_PATTERN = Pattern.compile("^$|^(https?|ftp)://[^\\s/$.?#].[^\\s]*$");

    public void validate(UserPreferences preferences) {
        if (preferences.getDisplayName() != null && preferences.getDisplayName().length() > 45) {
            throw new IllegalArgumentException("wrong parameters: display name too long (max 45 characters)");
        }

        if (preferences.getColor() != null && !preferences.getColor().isEmpty() && !HEX_COLOR_PATTERN.matcher(preferences.getColor()).matches()) {
            throw new IllegalArgumentException("wrong parameters: invalid color hex value");
        }


        if (preferences.getAvatarUrl() != null && !preferences.getAvatarUrl().isEmpty() && !URL_PATTERN.matcher(preferences.getAvatarUrl()).matches()) {
            throw new IllegalArgumentException("wrong parameters: invalid avatar URL");
        }

    }
}
