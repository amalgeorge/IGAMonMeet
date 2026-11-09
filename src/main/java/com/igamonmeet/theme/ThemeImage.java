package com.igamonmeet.theme;

import java.time.Instant;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "theme_image")
public class ThemeImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String contentType;
    private byte[] content;
    private int width;
    private int height;
    private String uploadedBy;
    private Instant uploadedAt;

    protected ThemeImage() {
    }

    ThemeImage(String contentType, byte[] content, int width, int height, String uploadedBy, Instant uploadedAt) {
        this.contentType = contentType;
        this.content = content;
        this.width = width;
        this.height = height;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() { return id; }
    public String getContentType() { return contentType; }
    public byte[] getContent() { return content; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
