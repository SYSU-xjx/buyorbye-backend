-- =============================================================================
--  V5 — Translate reference data to English
--
--  Keep the existing IDs and slugs stable while replacing user-facing Chinese
--  labels and descriptions in the reference data.
-- =============================================================================

SET NAMES utf8mb4;

UPDATE tags
SET name = CASE slug
  WHEN 'general' THEN 'General'
  WHEN 'life'    THEN 'Lifestyle'
  WHEN 'food'    THEN 'Food'
  WHEN 'outfit'  THEN 'Outfit'
  WHEN 'beauty'  THEN 'Beauty'
  WHEN 'digital' THEN 'Digital'
  WHEN 'idol'    THEN 'Idols'
  WHEN 'acg'     THEN 'Anime & Comics'
  WHEN 'game'    THEN 'Gaming'
  WHEN 'art'     THEN 'Art'
  WHEN 'travel'  THEN 'Travel'
  WHEN 'sports'  THEN 'Sports'
  WHEN 'pet'     THEN 'Pets'
  WHEN 'study'   THEN 'Study'
  WHEN 'career'  THEN 'Career'
  WHEN 'emotion' THEN 'Emotions'
END
WHERE slug IN (
  'general', 'life', 'food', 'outfit', 'beauty', 'digital', 'idol', 'acg',
  'game', 'art', 'travel', 'sports', 'pet', 'study', 'career', 'emotion'
);

UPDATE channels
SET
  name = CASE slug
    WHEN 'random'   THEN 'Browse Around'
    WHEN 'milk-tea' THEN 'Milk Tea'
    WHEN 'digital'  THEN 'Tech Finds'
    WHEN 'food'     THEN 'Food'
    WHEN 'fashion'  THEN 'Fashion & Beauty'
  END,
  description = CASE slug
    WHEN 'random'   THEN 'Stumble upon a dilemma and share your thoughts'
    WHEN 'milk-tea' THEN 'Which shop should we try today?'
    WHEN 'digital'  THEN 'Ask before you buy'
    WHEN 'food'     THEN 'What should we eat?'
    WHEN 'fashion'  THEN 'Is it worth it?'
  END
WHERE slug IN ('random', 'milk-tea', 'digital', 'food', 'fashion');
