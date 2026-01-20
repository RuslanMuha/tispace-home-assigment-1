--liquibase formatted sql

--changeset tispace:002-add-unique-index-title
CREATE UNIQUE INDEX IF NOT EXISTS uk_articles_title ON articles(title);


