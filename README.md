journal-visualisation
=====================

Visualising time-related data using the Gephi Toolkit. Creation of a video out of generated PNG images with ffmpeg.

SQL Query for Edge-Table
========================

SET @time = 0;
SET @end = 0;
SET @visitID = 1;
CREATE TABLE edges
SELECT
	page1.newsID as source,
	page2.newsID as target,
	(@time := @time + 1) as start,
	IF(page1.visitID <> @visitID,@end:=@time+(SELECT COUNT(*)-1 FROM page WHERE visitID = page1.visitID),@end) as end,
	page2.date as date,
	@visitID := page1.visitID as user
FROM
	page as page1,
	page as page2
WHERE
	page1.visitID = page2.visitID AND
	page1.date < page2.date AND
	(SELECT COUNT(*) FROM page as p WHERE p.visitID = page1.visitID) > 5 AND
	(SELECT COUNT(*) FROM page as p WHERE p.visitID = page1.visitID) < 20 AND
	page2.date = (
		SELECT MIN(page.date) 
		FROM page 
		WHERE 
			page.visitID = page1.visitID AND 
			page.date > page1.date
	)
ORDER BY user,date asc
