package io.jharness.cli;

import io.jharness.api.*;
import java.util.*;

/** Explicit named composition. Base < preset < profile settings; bundles must agree. */
final class ConfigurationSelection {
    private ConfigurationSelection() {}

    private static final Set<String> CATALOG =
            Set.of("models", "profiles", "bundles", "presets", "defaultProfile");

    static CliConfig resolve(CliConfig source, Arguments args) {
        Map<String, Object> resolved = new LinkedHashMap<>(source.values);
        CATALOG.forEach(resolved::remove);
        String profileName = args.get("profile", optionalText(source.values, "defaultProfile"));
        Map<String, Object> profile =
                profileName == null ? Map.of() : named(source, "profiles", profileName);
        if (!Set.of("modelRoute", "preset", "bundles", "settings").containsAll(profile.keySet()))
            throw error("INVALID_CONFIG");
        String presetName = args.get("preset", optionalText(profile, "preset"));
        Map<String, Object> preset =
                presetName == null ? Map.of() : named(source, "presets", presetName);
        if (!Set.of("modelRoute", "settings").containsAll(preset.keySet()))
            throw error("INVALID_CONFIG");
        resolved.putAll(settings(preset));
        resolved.putAll(settings(profile));
        String presetRoute = optionalText(preset, "modelRoute"),
                profileRoute = optionalText(profile, "modelRoute");
        if (!args.has("model-route")
                && presetRoute != null
                && profileRoute != null
                && !presetRoute.equals(profileRoute)) throw error("CONFIG_CONFLICT");
        String route = args.get("model-route", profileRoute == null ? presetRoute : profileRoute);
        if (route != null && !route.equals("base")) {
            var model = named(source, "models", route);
            if (!Set.of(
                            "baseUrl",
                            "model",
                            "apiKeyEnv",
                            "requestTimeoutSeconds",
                            "contextTokens",
                            "maxOutputTokens",
                            "inputTokenCeiling",
                            "maxCost",
                            "currency",
                            "inputPerMillion",
                            "outputPerMillion",
                            "disableThinking",
                            "protocol",
                            "reasoningEffort",
                            "toolsSupported",
                            "streamSupported")
                    .containsAll(model.keySet())) throw error("INVALID_CONFIG");
            resolved.putAll(model);
        }
        List<String> bundleNames = new ArrayList<>();
        if (profile.containsKey("bundles")) {
            if (!(profile.get("bundles") instanceof List<?> list)
                    || list.stream().anyMatch(v -> !(v instanceof String)))
                throw error("INVALID_CONFIG");
            list.forEach(v -> bundleNames.add((String) v));
        }
        bundleNames.addAll(args.all("bundle"));
        Map<String, Object> bundleSettings = new LinkedHashMap<>();
        Set<String> tools = new LinkedHashSet<>();
        List<String> prompts = new ArrayList<>();
        boolean toolsDeclared = false, promptsDeclared = false;
        for (String name : new LinkedHashSet<>(bundleNames)) {
            var bundle = named(source, "bundles", name);
            if (!Set.of("enabledTools", "prompts", "policy").containsAll(bundle.keySet()))
                throw error("INVALID_CONFIG");
            if (bundle.containsKey("enabledTools")) {
                toolsDeclared = true;
                tools.addAll(strings(bundle.get("enabledTools")));
            }
            if (bundle.containsKey("prompts")) {
                promptsDeclared = true;
                prompts.addAll(strings(bundle.get("prompts")));
            }
            if (bundle.containsKey("policy")) {
                if (!(bundle.get("policy") instanceof String policy)
                        || !Set.of("default", "read-only").contains(policy))
                    throw error("INVALID_CONFIG");
                Object prior = bundleSettings.putIfAbsent("policy", bundle.get("policy"));
                if (prior != null && !prior.equals(bundle.get("policy")))
                    throw error("CONFIG_CONFLICT");
            }
        }
        if ("read-only".equals(resolved.get("policy"))
                && "default".equals(bundleSettings.get("policy"))) throw error("CONFIG_CONFLICT");
        resolved.putAll(bundleSettings);
        if (toolsDeclared) resolved.put("enabledTools", List.copyOf(tools));
        if (promptsDeclared) resolved.put("prompts", List.copyOf(prompts));
        Map<String, Object> selection = new LinkedHashMap<>();
        selection.put("profile", profileName == null ? "base" : profileName);
        selection.put("preset", presetName == null ? "base" : presetName);
        selection.put("modelRoute", route == null ? "base" : route);
        selection.put("bundles", List.copyOf(new LinkedHashSet<>(bundleNames)));
        selection.put(
                "resolvedSha256",
                Json.hash(Json.text(resolved).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        if (source.values.containsKey("models"))
            resolved.put("models", source.values.get("models"));
        return new CliConfig(resolved, selection);
    }

    private static String optionalText(Map<String, Object> map, String key) {
        if (!map.containsKey(key)) return null;
        if (!(map.get(key) instanceof String text) || text.isBlank()) throw error("INVALID_CONFIG");
        return text;
    }

    private static Map<String, Object> named(CliConfig config, String kind, String name) {
        if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"))
            throw error("INVALID_CONFIG_SELECTION");
        Object raw = Values.object(config.values.getOrDefault(kind, Map.of())).get(name);
        if (!(raw instanceof Map<?, ?>)) throw error("CONFIG_SELECTION_NOT_FOUND");
        return Values.object(raw);
    }

    private static Map<String, Object> settings(Map<String, Object> entry) {
        var settings = Values.object(entry.getOrDefault("settings", Map.of()));
        if (settings.keySet().stream().anyMatch(CATALOG::contains)) throw error("INVALID_CONFIG");
        return settings;
    }

    private static List<String> strings(Object raw) {
        if (!(raw instanceof List<?> list)
                || list.size() > 64
                || list.stream().anyMatch(v -> !(v instanceof String)))
            throw error("INVALID_CONFIG");
        return list.stream().map(String.class::cast).toList();
    }

    private static HarnessException error(String code) {
        return new HarnessException(code, "Invalid or conflicting named configuration");
    }
}
