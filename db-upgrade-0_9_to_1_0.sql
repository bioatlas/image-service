alter table image add date_deleted timestamp;
alter table image add recognised_license_id bigint;
alter table image add occurrence_id character varying(255);
alter table image add calibrated_by_user character varying(255);

CREATE TABLE public.license (
    id bigint NOT NULL,
    version bigint NOT NULL,
    url character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    image_url character varying(255),
    acronym character varying(255) NOT NULL
);
ALTER TABLE ONLY public.license
    ADD CONSTRAINT license_pkey PRIMARY KEY (id);
CREATE TABLE public.license_mapping (
    id bigint NOT NULL,
    version bigint NOT NULL,
    value character varying(255) NOT NULL,
    license_id bigint NOT NULL
);
ALTER TABLE ONLY public.license_mapping
    ADD CONSTRAINT license_mapping_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.license_mapping
    ADD CONSTRAINT fktetx2lihs6cq4swvdhaqeco7s FOREIGN KEY (license_id) REFERENCES public.license(id);


update image set mime_type = 'audio/mpeg' where original_filename like '%.mp3';


/* Add the occurrence_id to top level table */
CREATE TEMP TABLE temp_image_occurrence as select image_id, value as occurrence_id from image_meta_data_item imdi where imdi.name='occurrenceId';
UPDATE image
SET occurrence_id = subquery.occurrence_id
FROM (SELECT io.image_id, io.occurrence_id from temp_image_occurrence io) AS subquery
WHERE image.id = subquery.image_id;

DROP TABLE temp_image_occurrence;
