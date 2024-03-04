import pytest


# Init client
@pytest.fixture(scope="module")
def client():
    import tftpclient
    return tftpclient.TFTPClient(('localhost', 12345), '/home/ubuntu/tftpdir/')


# Get existing 50 byte file
def test_GSBSmall(client):
    assert client.getFile(b'f50b.bin')


# Get existing 500 byte file
def test_GSBLarge(client):
    assert client.getFile(b'f500b.bin')


# Get existing 1,535 byte file
def test_GMB3(client):
    assert client.getFile(b'f3blks.bin')


# Get existing 262,143 byte file
def test_GMB512(client):
    assert client.getFile(b'f512blks.bin')


# Put 50 byte file
def test_PSB50B(client):
    assert client.putFileBytes(b'f50b.ul', 50)


# Put 500 byte file
def test_PSB500B(client):
    assert client.putFileBytes(b'f500b.ul', 500)


# Put 512 byte file
def test_PMB1Blks(client):
    assert client.putFileBlocks(b'f1blk.ul', 1)


# Put 1,536 byte file
def test_PMB3Blks(client):
    assert client.putFileBlocks(b'f3blks.ul', 3)


# Put 262,144 byte file
def test_PMB512Blks(client):
    assert client.putFileBlocks(b'f512blks.ul', 512)


# Try to get a file that does not exist
def test_GFileNotExists(client):
    assert client.getFileNotExists(b'nosuchfile')


# Send unknown request type
def test_BadOp10(client):
    assert client.sendBadOp(10)


# Send an unknown request type (similar to an existing)
def test_BadOp257(client):
    assert client.sendBadOp(257)


# Get a large file and fail the first ACK every time
def test_GMBFail1stAck(client):
    assert client.getMultiBlockFileFailAck(b'f3blks.bin', 1)


# Get a large file and fail the first two ACKs every time
def test_GMBFail2ndAck(client):
    assert client.getMultiBlockFileFailAck(b'f3blks.bin', 2)
