# Hotel API contract v1

Implementation contract. Base /api, JSON except login form. Java 26 + Spring Boot 4.0 (Java 26 compatibility). Roles GUEST, FRONTDESK. Demo accounts guest1 / guest2 / frontdesk all password Demo12345!.

## Shared

Error: {code,message,requestId}; 400 invalid input, 401 unauthenticated, 403 role/CSRF, 404 missing or someone else's order, 409 business conflict, 503 DATABASE_BUSY.
Dates ISO YYYY-MM-DD; UTC timestamps ISO strings; display Asia/Shanghai. IDs numeric; cents integer. Page {items,page,size,total}, page 1+, size default20 max100. All order lists createdAt DESC,id DESC.

GET /meta public → {hotelName:"栖岸酒店",today,availableFrom,availableTo}; availableTo is maximum checkout date. POST operations require CSRF except none; get CSRF before login, refresh after login/logout.
GET /auth/csrf public → {token,headerName}; POST /auth/login form username,password → {id,username,role}; POST /auth/logout → 200/204; GET /auth/me → {id,username,role}.

Order: {id,orderNo,roomTypeId,roomTypeName,checkIn,checkOut,guestCount,nightlyPriceCents,totalCents,status,createdAt,stay:null|{roomId,roomNumber,guestName,guestPhone,checkedInAt,checkedOutAt},events:[{fromStatus,toStatus,reason,createdAt}]}.
Status CONFIRMED,CANCELLED,NO_SHOW,CHECKED_IN,COMPLETED.
Room: {id,roomNumber,roomTypeId,roomTypeName,cleaningStatus:"CLEAN"|"DIRTY",occupied:boolean,overdue:boolean,currentOrderNo:null|string}.

## Guest

GET /room-types?checkIn=&checkOut= → [{id,name,description,capacity,nightlyPriceCents,availableRooms}]. Public read allowed. Full date coverage required; invalid/out-of-range dates 400. Sold-out room types return availableRooms:0.
POST /orders JSON {roomTypeId,checkIn,checkOut,guestCount}, header Idempotency-Key (1..100 chars) → Order; new 201, same key same parameters 200, different parameters 409 IDEMPOTENCY_CONFLICT. GUEST only.
GET /orders?page=&size=&status=&orderNo=&checkIn= → Page<Order>, own orders only.
GET /orders/{id} → Order, own only.
POST /orders/{id}/cancel → Order; before arrival date only, confirmed only, duplicate 409.

## Frontdesk (FRONTDESK only)

GET /frontdesk/orders same filters → Page<Order>, all orders.
GET /frontdesk/orders/{id} → Order.
GET /frontdesk/orders/{id}/available-rooms → Room[], only valid same-day confirmed order.
POST /frontdesk/orders/{id}/check-in {roomId,guestName,guestPhone} → Order.
POST /frontdesk/orders/{id}/check-out → Order; date >= checkout date, checked-in only.
POST /frontdesk/orders/{id}/cancel → Order; date <= arrival, confirmed only.
POST /frontdesk/orders/{id}/no-show → Order; today > arrival, confirmed only.
GET /frontdesk/rooms → Room[].
POST /frontdesk/rooms/{id}/clean → Room; reject occupied room, clean empty room idempotent.

Business errors: INVENTORY_INSUFFICIENT, ORDER_STATE_CONFLICT, ROOM_UNAVAILABLE, IDEMPOTENCY_CONFLICT, DATABASE_BUSY, VALIDATION_ERROR, NOT_FOUND, UNAUTHORIZED, FORBIDDEN.
Guest name 1..80 trimmed; phone 6..30 characters matching digits,+,space,hyphen; guestCount 1..capacity. Order number filter exact match. Invalid enum/pagination/date returns 400. Order checkIn filter exact date.

## Runtime

Backend default port 8080 override SERVER_PORT; SQLite path HOTEL_DB default ../data/hotel.db when run in backend. Clock system Asia/Shanghai; acceptance-only fixed clock supported ONLY with acceptance Spring profile. Demo data D-7..D+89; IDs guest1=1 guest2=2 frontdesk=3; room types and seed order IDs documented by backend implementer.
Frontend 5173 proxy /api to env API_TARGET default http://127.0.0.1:8080. Deploy single host via frontend proxy while independently building each project.
