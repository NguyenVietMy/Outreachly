-- Migrate existing ADMIN roles to OWNER
UPDATE users SET role = 'OWNER' WHERE role = 'ADMIN';

-- Replace the role check constraint to allow USER, OWNER, PREMIUM_USER
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('USER', 'OWNER', 'PREMIUM_USER')); 