(function () {
  'use strict';

  const estado = {
    token: localStorage.getItem('torven_token'),
    usuario: JSON.parse(localStorage.getItem('torven_usuario') || 'null'),
    clienteModo: 'registrado', // 'registrado' o 'nuevo'
    cliente: null,
    distritos: [],
    carrito: [] // { idProducto, nombre, precio, stock, cantidad }
  };

  const el = (id) => document.getElementById(id);

  const pantallaLogin = el('pantalla-login');
  const pantallaVenta = el('pantalla-venta');
  const pantallaConfirmacion = el('pantalla-confirmacion');

  // ---------- Arranque ----------

  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('/sw.js').catch(function () {
      // Si falla el service worker la app sigue funcionando, solo sin cache.
    });
  }

  if (estado.token && estado.usuario) {
    mostrarPantallaVenta();
  } else {
    mostrarPantalla(pantallaLogin);
  }

  // ---------- Navegacion entre pantallas ----------

  function mostrarPantalla(pantalla) {
    [pantallaLogin, pantallaVenta, pantallaConfirmacion].forEach(function (p) {
      p.classList.toggle('oculto', p !== pantalla);
    });
  }

  function mostrarPantallaVenta() {
    el('nombre-usuario').textContent = estado.usuario.nombre || estado.usuario.usuario;
    el('rol-usuario').textContent = estado.usuario.rol || '';
    mostrarPantalla(pantallaVenta);
    cargarDistritosSiHaceFalta();
  }

  // ---------- Llamadas a la API ----------

  function apiFetch(ruta, opciones) {
    opciones = opciones || {};
    opciones.headers = Object.assign({}, opciones.headers, {
      'Authorization': 'Bearer ' + estado.token
    });
    return fetch(ruta, opciones).then(function (respuesta) {
      if (respuesta.status === 401) {
        cerrarSesion();
        throw new Error('Tu sesion expiro. Inicia sesion de nuevo.');
      }
      return respuesta.json().then(function (cuerpo) {
        if (!respuesta.ok) {
          throw new Error(cuerpo.error || 'Ocurrio un error.');
        }
        return cuerpo;
      });
    });
  }

  // ---------- Login ----------

  el('form-login').addEventListener('submit', function (evento) {
    evento.preventDefault();
    const usuario = el('campo-usuario').value.trim();
    const clave = el('campo-clave').value;
    const errorLogin = el('error-login');
    errorLogin.textContent = '';

    fetch('/api/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ usuario: usuario, clave: clave })
    }).then(function (respuesta) {
      return respuesta.json().then(function (cuerpo) {
        if (!respuesta.ok) {
          throw new Error(cuerpo.error || 'No se pudo iniciar sesion.');
        }
        return cuerpo;
      });
    }).then(function (datos) {
      estado.token = datos.token;
      estado.usuario = datos;
      localStorage.setItem('torven_token', datos.token);
      localStorage.setItem('torven_usuario', JSON.stringify(datos));
      el('campo-clave').value = '';
      mostrarPantallaVenta();
    }).catch(function (error) {
      errorLogin.textContent = error.message;
    });
  });

  el('boton-salir').addEventListener('click', cerrarSesion);

  function cerrarSesion() {
    if (estado.token) {
      fetch('/api/logout', {
        method: 'POST',
        headers: { 'Authorization': 'Bearer ' + estado.token }
      }).catch(function () { /* no importa si falla, igual limpiamos local */ });
    }
    estado.token = null;
    estado.usuario = null;
    estado.cliente = null;
    estado.clienteModo = 'registrado';
    estado.carrito = [];
    localStorage.removeItem('torven_token');
    localStorage.removeItem('torven_usuario');
    document.querySelectorAll('#cliente-tipo .segmento').forEach(function (b) {
      b.classList.toggle('activo', b.getAttribute('data-tipo') === 'registrado');
    });
    el('cliente-modo-registrado').classList.remove('oculto');
    el('cliente-modo-nuevo').classList.add('oculto');
    actualizarCarritoUI();
    mostrarPantalla(pantallaLogin);
  }

  // ---------- Busqueda de clientes ----------

  const listaClientes = el('lista-clientes');
  const chipCliente = el('cliente-seleccionado');

  el('buscar-cliente').addEventListener('input', debounce(function (evento) {
    const texto = evento.target.value.trim();
    if (texto.length === 0) {
      listaClientes.innerHTML = '';
      return;
    }
    apiFetch('/api/clientes?buscar=' + encodeURIComponent(texto)).then(function (clientes) {
      renderizarClientes(clientes);
    }).catch(mostrarErrorVenta);
  }, 300));

  function renderizarClientes(clientes) {
    listaClientes.innerHTML = '';
    clientes.slice(0, 15).forEach(function (cliente) {
      const li = document.createElement('li');
      li.innerHTML =
        '<div class="item-titulo">' + escapar(cliente.nombre) + '</div>' +
        '<div class="item-detalle">' + escapar(cliente.numero || '') +
        (cliente.distrito ? ' - ' + escapar(cliente.distrito) : '') + '</div>';
      li.addEventListener('click', function () {
        estado.cliente = cliente;
        el('cliente-nombre-chip').textContent = cliente.nombre;
        chipCliente.classList.remove('oculto');
        el('buscar-cliente').value = '';
        listaClientes.innerHTML = '';
        actualizarBotonConfirmar();
      });
      listaClientes.appendChild(li);
    });
  }

  el('quitar-cliente').addEventListener('click', function () {
    estado.cliente = null;
    chipCliente.classList.add('oculto');
    actualizarBotonConfirmar();
  });

  // ---------- Tipo de cliente: registrado o nuevo ----------

  const modoRegistrado = el('cliente-modo-registrado');
  const modoNuevo = el('cliente-modo-nuevo');
  const selectDistrito = el('nuevo-cliente-distrito');

  document.querySelectorAll('#cliente-tipo .segmento').forEach(function (boton) {
    boton.addEventListener('click', function () {
      document.querySelectorAll('#cliente-tipo .segmento').forEach(function (b) {
        b.classList.remove('activo');
      });
      boton.classList.add('activo');
      estado.clienteModo = boton.getAttribute('data-tipo');
      modoRegistrado.classList.toggle('oculto', estado.clienteModo !== 'registrado');
      modoNuevo.classList.toggle('oculto', estado.clienteModo !== 'nuevo');
      actualizarBotonConfirmar();
    });
  });

  let distritosCargados = false;

  function cargarDistritosSiHaceFalta() {
    if (distritosCargados) {
      return;
    }
    apiFetch('/api/distritos').then(function (distritos) {
      distritosCargados = true;
      estado.distritos = distritos;
      selectDistrito.innerHTML = '';
      distritos.forEach(function (distrito) {
        const opcion = document.createElement('option');
        opcion.value = distrito.id;
        opcion.textContent = distrito.nombre;
        if (distrito.nombre === 'Otro') {
          opcion.selected = true;
        }
        selectDistrito.appendChild(opcion);
      });
    }).catch(function () {
      // Si falla, el select queda vacio; se puede reintentar mostrando la pantalla de nuevo.
    });
  }

  ['nuevo-cliente-nombre', 'nuevo-cliente-direccion'].forEach(function (id) {
    el(id).addEventListener('input', actualizarBotonConfirmar);
  });

  function datosClienteNuevoCompletos() {
    return el('nuevo-cliente-nombre').value.trim().length > 0
        && el('nuevo-cliente-direccion').value.trim().length > 0;
  }

  function limpiarFormularioClienteNuevo() {
    el('nuevo-cliente-nombre').value = '';
    el('nuevo-cliente-telefono').value = '';
    el('nuevo-cliente-direccion').value = '';
  }

  // ---------- Busqueda de productos ----------

  const listaProductos = el('lista-productos');

  el('buscar-producto').addEventListener('input', debounce(function (evento) {
    const texto = evento.target.value.trim();
    apiFetch('/api/productos?buscar=' + encodeURIComponent(texto)).then(function (productos) {
      renderizarProductos(productos);
    }).catch(mostrarErrorVenta);
  }, 300));

  function renderizarProductos(productos) {
    listaProductos.innerHTML = '';
    productos.slice(0, 20).forEach(function (producto) {
      const agotado = producto.stock <= 0;
      const li = document.createElement('li');
      if (agotado) {
        li.style.opacity = '0.55';
      }
      li.innerHTML =
        '<div class="item-titulo">' + escapar(producto.nombre) + '</div>' +
        '<div class="item-detalle' + (agotado ? ' agotado' : '') + '">' +
        'S/ ' + Number(producto.precio).toFixed(2) + ' &middot; Stock: ' + producto.stock + '</div>';
      if (!agotado) {
        li.addEventListener('click', function () {
          agregarAlCarrito(producto);
        });
      }
      listaProductos.appendChild(li);
    });
  }

  function agregarAlCarrito(producto) {
    const existente = estado.carrito.find(function (l) { return l.idProducto === producto.id; });
    if (existente) {
      if (existente.cantidad < producto.stock) {
        existente.cantidad += 1;
      }
    } else {
      estado.carrito.push({
        idProducto: producto.id,
        nombre: producto.nombre,
        precio: Number(producto.precio),
        stock: producto.stock,
        cantidad: 1
      });
    }
    actualizarCarritoUI();
  }

  // ---------- Carrito ----------

  const listaCarrito = el('carrito');
  const carritoVacio = el('carrito-vacio');

  function actualizarCarritoUI() {
    listaCarrito.innerHTML = '';
    carritoVacio.classList.toggle('oculto', estado.carrito.length > 0);

    let total = 0;
    estado.carrito.forEach(function (linea, indice) {
      const subtotal = linea.precio * linea.cantidad;
      total += subtotal;

      const li = document.createElement('li');
      li.innerHTML =
        '<div class="carrito-info">' +
        '  <div class="item-titulo">' + escapar(linea.nombre) + '</div>' +
        '  <div class="item-detalle">S/ ' + linea.precio.toFixed(2) + ' c/u &middot; S/ ' + subtotal.toFixed(2) + '</div>' +
        '</div>' +
        '<div class="carrito-cantidad">' +
        '  <button data-accion="menos" type="button">-</button>' +
        '  <span>' + linea.cantidad + '</span>' +
        '  <button data-accion="mas" type="button">+</button>' +
        '  <button class="carrito-quitar" data-accion="quitar" type="button">&times;</button>' +
        '</div>';

      li.querySelector('[data-accion="menos"]').addEventListener('click', function () {
        cambiarCantidad(indice, -1);
      });
      li.querySelector('[data-accion="mas"]').addEventListener('click', function () {
        cambiarCantidad(indice, 1);
      });
      li.querySelector('[data-accion="quitar"]').addEventListener('click', function () {
        estado.carrito.splice(indice, 1);
        actualizarCarritoUI();
      });

      listaCarrito.appendChild(li);
    });

    el('total-carrito').textContent = 'S/ ' + total.toFixed(2);
    actualizarBotonConfirmar();
  }

  function cambiarCantidad(indice, delta) {
    const linea = estado.carrito[indice];
    const nueva = linea.cantidad + delta;
    if (nueva <= 0) {
      estado.carrito.splice(indice, 1);
    } else if (nueva <= linea.stock) {
      linea.cantidad = nueva;
    }
    actualizarCarritoUI();
  }

  function actualizarBotonConfirmar() {
    const hayCliente = estado.clienteModo === 'nuevo'
        ? datosClienteNuevoCompletos()
        : !!estado.cliente;
    el('boton-confirmar').disabled = !hayCliente || estado.carrito.length === 0;
  }

  // ---------- Confirmar venta ----------

  function obtenerIdCliente() {
    if (estado.clienteModo === 'registrado') {
      return Promise.resolve(estado.cliente.id);
    }
    const cuerpo = {
      nombre: el('nuevo-cliente-nombre').value.trim(),
      numero: el('nuevo-cliente-telefono').value.trim(),
      direccion: el('nuevo-cliente-direccion').value.trim(),
      idDistrito: Number(selectDistrito.value) || 0
    };
    return apiFetch('/api/clientes', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(cuerpo)
    }).then(function (clienteCreado) {
      return clienteCreado.id;
    });
  }

  el('boton-confirmar').addEventListener('click', function () {
    const boton = el('boton-confirmar');
    boton.disabled = true;
    el('error-venta').textContent = '';

    obtenerIdCliente().then(function (idCliente) {
      const cuerpo = {
        idCliente: idCliente,
        lineas: estado.carrito.map(function (l) {
          return { idProducto: l.idProducto, cantidad: l.cantidad };
        })
      };
      return apiFetch('/api/ventas', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(cuerpo)
      });
    }).then(function (ticket) {
      el('detalle-confirmacion').textContent =
        'Venta #' + ticket.idVenta + ' - Total S/ ' + Number(ticket.total).toFixed(2);
      estado.cliente = null;
      estado.carrito = [];
      chipCliente.classList.add('oculto');
      limpiarFormularioClienteNuevo();
      actualizarCarritoUI();
      mostrarPantalla(pantallaConfirmacion);
    }).catch(function (error) {
      mostrarErrorVenta(error);
      actualizarBotonConfirmar();
    });
  });

  el('boton-nueva-venta').addEventListener('click', function () {
    mostrarPantallaVenta();
  });

  function mostrarErrorVenta(error) {
    el('error-venta').textContent = error.message || 'Ocurrio un error.';
  }

  // ---------- Utilidades ----------

  function debounce(fn, esperaMs) {
    let temporizador;
    return function () {
      const args = arguments;
      const self = this;
      clearTimeout(temporizador);
      temporizador = setTimeout(function () { fn.apply(self, args); }, esperaMs);
    };
  }

  function escapar(texto) {
    const div = document.createElement('div');
    div.textContent = texto == null ? '' : String(texto);
    return div.innerHTML;
  }
})();
