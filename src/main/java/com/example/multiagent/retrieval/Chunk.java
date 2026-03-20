package com.example.multiagent.retrieval;

public class Chunk {
    private String docId;
    private String sectionTitle;
    private String text;

    public Chunk() {
    }

    public Chunk(String docId, String sectionTitle, String text) {
        this.docId = docId;
        this.sectionTitle = sectionTitle;
        this.text = text;
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public String getSectionTitle() {
        return sectionTitle;
    }

    public void setSectionTitle(String sectionTitle) {
        this.sectionTitle = sectionTitle;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
