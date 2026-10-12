package dto;

public class ReviewStatsDto {
    private double averageRating = 5.0;
    private String averageRatingFormatted = "5.0";
    private long totalReviews = 0;
    private long count5Star = 0;
    private long count4Star = 0;
    private long count3Star = 0;
    private long count2Star = 0;
    private long count1Star = 0;

    public ReviewStatsDto() {
    }

    public double getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(double averageRating) {
        this.averageRating = averageRating;
    }

    public String getAverageRatingFormatted() {
        return averageRatingFormatted;
    }

    public String getStars() {
        int count = (int) Math.round(averageRating);
        if (count < 1) count = 1;
        if (count > 5) count = 5;
        return "★".repeat(count);
    }

    public void setAverageRatingFormatted(String averageRatingFormatted) {
        this.averageRatingFormatted = averageRatingFormatted;
    }

    public long getTotalReviews() {
        return totalReviews;
    }

    public void setTotalReviews(long totalReviews) {
        this.totalReviews = totalReviews;
    }

    public long getCount5Star() {
        return count5Star;
    }

    public void setCount5Star(long count5Star) {
        this.count5Star = count5Star;
    }

    public long getCount4Star() {
        return count4Star;
    }

    public void setCount4Star(long count4Star) {
        this.count4Star = count4Star;
    }

    public long getCount3Star() {
        return count3Star;
    }

    public void setCount3Star(long count3Star) {
        this.count3Star = count3Star;
    }

    public long getCount2Star() {
        return count2Star;
    }

    public void setCount2Star(long count2Star) {
        this.count2Star = count2Star;
    }

    public long getCount1Star() {
        return count1Star;
    }

    public void setCount1Star(long count1Star) {
        this.count1Star = count1Star;
    }
}
