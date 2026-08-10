insert into users(id,email,password_hash,enabled) values
('10000000-0000-0000-0000-000000000001','admin@example.test','$2a$10$9AfQKnY5BhANDyX0RAMOXeYiVAM0XyXETDc.A2UdH50pw./0OU0Jq',true),
('10000000-0000-0000-0000-000000000002','instructor@example.test','$2a$10$9AfQKnY5BhANDyX0RAMOXeYiVAM0XyXETDc.A2UdH50pw./0OU0Jq',true),
('10000000-0000-0000-0000-000000000003','student@example.test','$2a$10$9AfQKnY5BhANDyX0RAMOXeYiVAM0XyXETDc.A2UdH50pw./0OU0Jq',true);

insert into user_roles(user_id,role_name) values
('10000000-0000-0000-0000-000000000001','ADMIN'),
('10000000-0000-0000-0000-000000000002','INSTRUCTOR'),
('10000000-0000-0000-0000-000000000003','STUDENT');

insert into instructors(id,user_id,name,email,biography) values
('20000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000002','Demo Instructor','instructor@example.test','Demo account');

insert into students(id,user_id,first_name,last_name,email) values
('30000000-0000-0000-0000-000000000003','10000000-0000-0000-0000-000000000003','Demo','Student','student@example.test');
