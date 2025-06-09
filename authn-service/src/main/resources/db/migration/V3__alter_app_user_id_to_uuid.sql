ALTER TABLE app_user
    ALTER COLUMN id SET DATA TYPE UUID USING (gen_random_uuid());