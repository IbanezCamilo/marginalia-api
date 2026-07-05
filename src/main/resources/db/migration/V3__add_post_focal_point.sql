-- Cover-image focal point (normalized [0,1], default center 0.5), rendered by the frontend as CSS object-position.
-- Default 0.5 keeps existing posts center-cropped without a data backfill.
alter table posts add column focal_x numeric(4,3) not null default 0.5;
alter table posts add column focal_y numeric(4,3) not null default 0.5;
