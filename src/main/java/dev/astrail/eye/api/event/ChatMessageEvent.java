package dev.astrail.eye.api.event;

/** Server game message after vanilla/Fabric filtering, stripped by consumers as needed. */
public record ChatMessageEvent(String text, boolean overlay) implements ClientEvent {
}
