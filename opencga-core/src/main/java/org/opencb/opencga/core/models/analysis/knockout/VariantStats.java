package org.opencb.opencga.core.models.analysis.knockout;

public class VariantStats {

    private int count;
    private int numHomAlt;
    private int numCompHet;
    private int numHetAlt;
    private int numDelOverlap;

    public VariantStats() {
    }

    public VariantStats(int count, int numHomAlt, int numCompHet, int numHetAlt, int numDelOverlap) {
        this.count = count;
        this.numHomAlt = numHomAlt;
        this.numCompHet = numCompHet;
        this.numHetAlt = numHetAlt;
        this.numDelOverlap = numDelOverlap;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("VariantStats{");
        sb.append("count=").append(count);
        sb.append(", numHomAlt=").append(numHomAlt);
        sb.append(", numCompHet=").append(numCompHet);
        sb.append(", numHetAlt=").append(numHetAlt);
        sb.append(", numDelOverlap=").append(numDelOverlap);
        sb.append('}');
        return sb.toString();
    }

    public int getCount() {
        return count;
    }

    public VariantStats setCount(int count) {
        this.count = count;
        return this;
    }

    public int getNumHomAlt() {
        return numHomAlt;
    }

    public VariantStats setNumHomAlt(int numHomAlt) {
        this.numHomAlt = numHomAlt;
        return this;
    }

    public int getNumCompHet() {
        return numCompHet;
    }

    public VariantStats setNumCompHet(int numCompHet) {
        this.numCompHet = numCompHet;
        return this;
    }

    public int getNumHetAlt() {
        return numHetAlt;
    }

    public VariantStats setNumHetAlt(int numHetAlt) {
        this.numHetAlt = numHetAlt;
        return this;
    }

    public int getNumDelOverlap() {
        return numDelOverlap;
    }

    public VariantStats setNumDelOverlap(int numDelOverlap) {
        this.numDelOverlap = numDelOverlap;
        return this;
    }

}
