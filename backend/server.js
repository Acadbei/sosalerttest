require('dotenv').config();
const express = require('express');
const cors = require('cors');
const http = require('http');
const { Server } = require('socket.io');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
    cors: { origin: '*' }
});

app.use(cors());
app.use(express.json());

// In-memory (Operativ xotira) arraylariga saqlash
// (Render bepul versiyasida testing uchun yetarli)
let alerts = [];
let safeZones = [];
let users = [];

app.get('/', (req, res) => {
    res.json({ status: "ok", message: "SOS Alert Backend is working fine!" });
});

// Alerts endpoints
app.get('/api/alerts', (req, res) => res.json(alerts));
app.post('/api/alerts', (req, res) => {
    const newAlert = req.body;
    alerts.push(newAlert);
    io.emit('new_alert', newAlert); // Barcha ulangan klientlarga signal
    res.status(201).json(newAlert);
});

// Safe Zones endpoints
app.get('/api/safe-zones', (req, res) => res.json(safeZones));
app.post('/api/safe-zones', (req, res) => {
    const newZone = req.body;
    safeZones.push(newZone);
    io.emit('new_safe_zone', newZone);
    res.status(201).json(newZone);
});

// Users endpoints
app.get('/api/users', (req, res) => res.json(users));
app.post('/api/users', (req, res) => {
    const newUser = req.body;
    users.push(newUser);
    res.status(201).json(newUser);
});
app.get('/health', (req, res) => {
  res.status(200).json({
    status: 'ok'
  });
});
// Socket.io connection logic
io.on('connection', (socket) => {
    console.log('New client connected:', socket.id);
    socket.on('disconnect', () => {
        console.log('Client disconnected:', socket.id);
    });
});

const PORT = process.env.PORT || 10000;
server.listen(PORT, () => {
    console.log(`Server yondi: http://localhost:${PORT}`);
});
