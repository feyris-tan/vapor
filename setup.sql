create table games
(
	id int auto_increment
		primary key,
	uploaded datetime default 'current_timestamp()' not null,
	title varchar(255) not null,
	author varchar(255) default 'NULL' null,
	resource_dependency int default 'NULL' null,
	hide tinyint(1) default '0' not null,
	datafile longblob default 'NULL' null
)
;

create index game_title_index
	on games (title)
;

create table resources
(
	hashdeep varchar(112) not null
		primary key,
	uploaded datetime default 'current_timestamp()' not null,
	originalFilename varchar(255) default 'NULL' null,
	data mediumblob not null,
	constraint resources_hash_unique
		unique (hashdeep)
)
;

create index resources_hash_index
	on resources (hashdeep)
;

