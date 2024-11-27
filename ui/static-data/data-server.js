const express = require('express');
const cors = require('cors');
const app = express();

app.use(cors());
app.use('/assets/data', express.static('data'));
app.use('/data', express.static('data'));

const PORT = 3000;
app.listen(PORT, () => {
  console.log(`Data server running on port ${PORT}`);
});