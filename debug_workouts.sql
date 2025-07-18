-- SQL queries to debug completed workouts issue

-- Check all workouts in database
SELECT id, user_id, name, status, date, start_time, end_time, created_at 
FROM workouts 
ORDER BY created_at DESC 
LIMIT 10;

-- Check specifically for completed workouts
SELECT id, user_id, name, status, date, start_time, end_time, created_at 
FROM workouts 
WHERE status = 'COMPLETED' 
ORDER BY date DESC, created_at DESC 
LIMIT 10;

-- Check for any workouts that might have different status values
SELECT DISTINCT status, COUNT(*) as count
FROM workouts 
GROUP BY status;

-- Check recent workouts for all users
SELECT user_id, COUNT(*) as total_workouts, 
       COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) as completed_workouts
FROM workouts 
GROUP BY user_id;