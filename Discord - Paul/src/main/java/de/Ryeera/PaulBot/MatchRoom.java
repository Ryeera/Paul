package de.Ryeera.PaulBot;

public class MatchRoom {

    private final String catID;
    private final long creatorID;
    private final String creatorName;
    private final String created;

    public MatchRoom(String catID,
                     long creatorID,
                     String creatorName,
                     String created) {
        this.catID = catID;
        this.creatorID = creatorID;
        this.creatorName = creatorName;
        this.created = created;
    }

    public String getCatID() {
        return catID;
    }

    public long getCreatorID() {
        return creatorID;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public String getCreated() {
        return created;
    }
}
